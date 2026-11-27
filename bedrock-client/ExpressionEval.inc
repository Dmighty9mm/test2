    // ===== PlebClient: a small built-in expression evaluator =====
    // Flarial used the "rift" library here, which is no longer downloadable. This
    // covers the same syntax: {value}, math, comparisons, a ? b : c, if(c, a, b),
    // string concatenation with +, and the color/utility functions above.
    namespace eval {
        struct Val {
            bool isStr = false;
            double num = 0;
            std::string str;
            static Val n(double d) { Val v; v.num = d; return v; }
            static Val s(std::string x) { Val v; v.isStr = true; v.str = std::move(x); return v; }
            double toNum() const {
                if (!isStr) return num;
                try { return std::stod(str); } catch (...) { return 0; }
            }
            bool truthy() const { return isStr ? !str.empty() : num != 0; }
            std::string toStr() const {
                if (isStr) return str;
                if (std::isfinite(num) && num == std::floor(num) && std::fabs(num) < 9.0e15) {
                    return std::to_string(static_cast<int64_t>(num));
                }
                std::ostringstream o;
                o << std::setprecision(6) << num;
                return o.str();
            }
        };

        struct Fail {};

        struct Parser {
            const std::string& t;
            size_t i = 0;
            const Val& value;
            Parser(const std::string& text, const Val& v) : t(text), value(v) {}

            void ws() { while (i < t.size() && std::isspace(static_cast<unsigned char>(t[i]))) i++; }
            bool eat(const char* s) {
                ws();
                size_t n = std::strlen(s);
                if (t.compare(i, n, s) == 0) { i += n; return true; }
                return false;
            }
            Val parse() { Val v = ternary(); ws(); if (i != t.size()) throw Fail{}; return v; }
            Val ternary() {
                Val c = orr();
                if (eat("?")) { Val a = ternary(); if (!eat(":")) throw Fail{}; Val b = ternary(); return c.truthy() ? a : b; }
                return c;
            }
            Val orr() { Val a = andd(); while (eat("||")) { Val b = andd(); a = Val::n(a.truthy() || b.truthy()); } return a; }
            Val andd() { Val a = cmp(); while (eat("&&")) { Val b = cmp(); a = Val::n(a.truthy() && b.truthy()); } return a; }
            Val cmp() {
                Val a = add();
                for (const char* op : {"==", "!=", "<=", ">=", "<", ">"}) {
                    if (eat(op)) {
                        Val b = add();
                        std::string o = op;
                        if (o == "==") return Val::n(a.isStr || b.isStr ? a.toStr() == b.toStr() : a.num == b.num);
                        if (o == "!=") return Val::n(a.isStr || b.isStr ? a.toStr() != b.toStr() : a.num != b.num);
                        double x = a.toNum(), y = b.toNum();
                        if (o == "<=") return Val::n(x <= y);
                        if (o == ">=") return Val::n(x >= y);
                        if (o == "<") return Val::n(x < y);
                        return Val::n(x > y);
                    }
                }
                return a;
            }
            Val add() {
                Val a = mul();
                for (;;) {
                    if (eat("+")) { Val b = mul(); a = (a.isStr || b.isStr) ? Val::s(a.toStr() + b.toStr()) : Val::n(a.num + b.num); }
                    else if (eat("-")) { Val b = mul(); a = Val::n(a.toNum() - b.toNum()); }
                    else return a;
                }
            }
            Val mul() {
                Val a = unary();
                for (;;) {
                    if (eat("*")) { a = Val::n(a.toNum() * unary().toNum()); }
                    else if (eat("/")) { double d = unary().toNum(); a = Val::n(d == 0 ? 0 : a.toNum() / d); }
                    else if (eat("%")) { double d = unary().toNum(); a = Val::n(d == 0 ? 0 : std::fmod(a.toNum(), d)); }
                    else return a;
                }
            }
            Val unary() {
                if (eat("-")) return Val::n(-unary().toNum());
                if (eat("!")) return Val::n(!unary().truthy());
                return primary();
            }
            Val primary() {
                ws();
                if (i >= t.size()) throw Fail{};
                char c = t[i];
                if (c == '(') { i++; Val v = ternary(); if (!eat(")")) throw Fail{}; return v; }
                if (c == '"' || c == '\'') {
                    char q = c; i++;
                    std::string s;
                    while (i < t.size() && t[i] != q) { if (t[i] == '\\' && i + 1 < t.size()) i++; s += t[i++]; }
                    if (i >= t.size()) throw Fail{};
                    i++;
                    return Val::s(s);
                }
                if (std::isdigit(static_cast<unsigned char>(c)) || c == '.') {
                    size_t start = i;
                    while (i < t.size() && (std::isdigit(static_cast<unsigned char>(t[i])) || t[i] == '.')) i++;
                    try { return Val::n(std::stod(t.substr(start, i - start))); } catch (...) { throw Fail{}; }
                }
                if (std::isalpha(static_cast<unsigned char>(c)) || c == '_') {
                    size_t start = i;
                    while (i < t.size() && (std::isalnum(static_cast<unsigned char>(t[i])) || t[i] == '_')) i++;
                    std::string name = t.substr(start, i - start);
                    if (eat("(")) {
                        std::vector<Val> args;
                        if (!eat(")")) {
                            do { args.push_back(ternary()); } while (eat(","));
                            if (!eat(")")) throw Fail{};
                        }
                        return call(name, args);
                    }
                    return var(name);
                }
                throw Fail{};
            }
            Val var(const std::string& n) {
                if (n == "value" || n == "v" || n == "val" || n == "VALUE" || n == "Value") return value;
                if (n == "true") return Val::n(1);
                if (n == "false") return Val::n(0);
                static const char* colors[] = {"red", "green", "blue", "yellow", "orange", "purple", "pink", "cyan",
                                               "white", "black", "gray", "reset"};
                for (const char* col : colors) if (n == col) return Val::s("{" + n + "}");
                throw Fail{};
            }
            Val call(const std::string& f, const std::vector<Val>& a) {
                auto need = [&](size_t k) { if (a.size() < k) throw Fail{}; };
                auto num = [&](size_t k) { return a[k].toNum(); };
                if (f == "if") { need(3); return a[0].truthy() ? a[1] : a[2]; }
                if (f == "colorRange") { need(3); return Val::s(detail::colorRange(num(0), num(1), num(2))); }
                if (f == "colorRangeInverse") { need(3); return Val::s(detail::colorRangeInverse(num(0), num(1), num(2))); }
                if (f == "colorGradient") { need(3); return Val::s(detail::colorGradient(num(0), num(1), num(2))); }
                if (f == "colorGradientInverse") { need(3); return Val::s(detail::colorGradientInverse(num(0), num(1), num(2))); }
                if (f == "rgb") { need(3); return Val::s(detail::rgbFunc(num(0), num(1), num(2))); }
                if (f == "hex") {
                    need(1);
                    std::string h = a[0].toStr();
                    if (!h.empty() && h[0] == '#') h = h.substr(1);
                    if (h.size() != 6) throw Fail{};
                    return Val::s("{#" + h + "}");
                }
                if (f == "percent") { need(2); return Val::s(detail::percentFunc(num(0), num(1))); }
                if (f == "round") { need(2); return Val::s(detail::roundFunc(num(0), num(1))); }
                if (f == "clamp") { need(3); return Val::s(detail::clampFunc(num(0), num(1), num(2))); }
                throw Fail{};
            }
        };
    } // namespace eval

    inline void initialize() { detail::initialized = true; }

    inline std::string format(const std::string& formatString, double value, const std::string& stringValue = "") {
        if (formatString.find('{') == std::string::npos) return formatString;
        eval::Val v = !stringValue.empty() ? eval::Val::s(stringValue) : eval::Val::n(value);
        std::string out;
        size_t i = 0;
        while (i < formatString.size()) {
            if (formatString[i] != '{') { out += formatString[i++]; continue; }
            // find the matching close brace, skipping quoted text and nested braces
            size_t j = i + 1;
            int depth = 1;
            char quote = 0;
            for (; j < formatString.size(); j++) {
                char c = formatString[j];
                if (quote) { if (c == '\\') j++; else if (c == quote) quote = 0; continue; }
                if (c == '"' || c == '\'') quote = c;
                else if (c == '{') depth++;
                else if (c == '}' && --depth == 0) break;
            }
            if (j >= formatString.size()) { out += formatString.substr(i); break; }
            std::string inner = formatString.substr(i + 1, j - i - 1);
            try {
                eval::Parser p(inner, v);
                out += p.parse().toStr();
            } catch (...) {
                out += formatString.substr(i, j - i + 1); // not an expression (a color tag): keep it
            }
            i = j + 1;
        }
        return out;
    }

