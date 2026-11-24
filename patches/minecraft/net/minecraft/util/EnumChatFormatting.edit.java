
# Eagler Context Redacted Diff
# Copyright (c) 2024 lax1dude. All rights reserved.

# Version: 1.0
# Author: lax1dude

> DELETE  2  @  2 : 4

> INSERT  5 : 8  @  5

+ import com.google.common.collect.Lists;
+ import com.google.common.collect.Maps;
+ 

> INSERT  9 : 11  @  9

+ 	public static final EnumChatFormatting[] _VALUES = values();
+ 

> CHANGE  50 : 52  @  50 : 51

~ 		return text == null ? null
~ 				: formattingCodePattern.matcher(net.plebmc.client.text.RgbText.strip(text)).replaceAll("");

> CHANGE  10 : 13  @  10 : 11

~ 			EnumChatFormatting[] types = _VALUES;
~ 			for (int i = 0; i < types.length; ++i) {
~ 				EnumChatFormatting enumchatformatting = types[i];

> CHANGE  12 : 15  @  12 : 13

~ 		EnumChatFormatting[] types = _VALUES;
~ 		for (int i = 0; i < types.length; ++i) {
~ 			EnumChatFormatting enumchatformatting = types[i];

> CHANGE  9 : 12  @  9 : 11

~ 		EnumChatFormatting[] types = _VALUES;
~ 		for (int i = 0; i < types.length; ++i) {
~ 			nameMapping.put(func_175745_c(types[i].name), types[i]);

> EOF
