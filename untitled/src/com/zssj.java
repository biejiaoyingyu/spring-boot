package com;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;

/**
 * Created by cxf on 2018/10/15.
 */
public class zssj {
	public static void main(String[] args) {
		FileInputStream inputStream = null;
		try {
			inputStream = new FileInputStream(new File("/data.txt"));
		} catch (FileNotFoundException e) {
			e.printStackTrace();
		}
		FileChannel fileChannel = inputStream.getChannel();

		ByteBuffer buffer = ByteBuffer.allocate(1024);

		try {
			int num = fileChannel.read(buffer);
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
}
