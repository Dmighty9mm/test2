package litebans.api;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;

/**
 * A stand-in for LiteBans' API class in the tests: the same two methods
 * PlebioAPI calls, answering from an H2 database (LiteBans' default) with
 * LiteBans' tables.
 */
public class Database {
	public static String url;
	public static boolean broken;
	private static final Database INSTANCE = new Database();

	public static Database get() {
		return INSTANCE;
	}

	public PreparedStatement prepareStatement(String sql) throws SQLException {
		if (broken) {
			throw new SQLException("database is closed");
		}
		Connection c = DriverManager.getConnection(url);
		for (String t : new String[] { "bans", "mutes", "warnings", "kicks", "history" }) {
			sql = sql.replace("{" + t + "}", "litebans_" + t);
		}
		PreparedStatement st = c.prepareStatement(sql);
		st.closeOnCompletion();
		return st;
	}
}
