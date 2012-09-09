package com.applegrew.sphinx.intersphinx;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.util.Arrays;
import java.util.zip.DeflaterOutputStream;
import java.util.zip.InflaterOutputStream;

public class EncodeDecodeObjectInv {
	
	public static final String NL = Character.toString((char) 0x0A);

	public static void main(String[] args) throws IOException {
		if (args.length < 1) {
			showUsage();
			return;
		}
		String cmd = args[0];
		if (cmd.equals("decode")) {
			String objectsInvPath = "objects.inv";
			OutputStream output = System.out;
			if (args.length >= 2) {
				objectsInvPath = args[1];
			}
			if (args.length >= 3) {
				output = new FileOutputStream(args[2]);
			}
			if (output == System.out) {
				decodeObjectsInv(new FileInputStream(objectsInvPath), output, true);
			} else {
				System.out.println(decodeObjectsInv(new FileInputStream(objectsInvPath), output, true));
			}
			
		} else if (cmd.equals("encode")) {
			if (args.length < 2) {
				showUsage();
				return;
			} else {
				String srcTxtFile = args[1];
				OutputStream output = System.out;
				if (args.length >= 3) {
					output = new FileOutputStream(args[2]);
				}
				encodeTxtToObjectsInv(srcTxtFile, output);
			}
			
		} else if (cmd.equals("djangofix")) {
			String objectsInvPath = "objects.inv";
			OutputStream output = System.out;
			if (args.length >= 2) {
				objectsInvPath = args[1];
			}
			if (args.length >= 3) {
				output = new FileOutputStream(args[2]);
			}
			Django.fixDjangoObjectsInv(new FileInputStream(objectsInvPath), output);
			
		} else {
			showUsage();
			return;
		}
		
	}
	
	private static void showUsage() {
		System.out.println("=====Usage=====");
		System.out.println("cmd [options]");
		System.out.println("cmd: decode encode djangofix");
		System.out.println();
		System.out.println("decode options: <objects.inv file name with path. Default is: objects.inv> <output file. Default is stdout.>");
		System.out.println();
		System.out.println("encode options: <source text file name with path which to encode as objects.inv. The first four lines will be treated as objects.inv header and hence won't be encoded.>\n\t<output file. Default is stdout.");
		System.out.println();
		System.out.println("djangofix options: <Django objects.inv file name with path. Default is: objects.inv> <output file. Default is stdout.>");
		System.exit(1);
	}
	
	public static String decodeObjectsInv(InputStream objectsInv, OutputStream outputStream, boolean outputHeaderAlso) throws IOException {
		StringBuffer header = new StringBuffer();
		InputStream input = null;
		OutputStream output = null;
		try {
			input = new BufferedInputStream(objectsInv);
			output = new BufferedOutputStream(outputStream);
			
			boolean isHeaderParsed = false;
			int remainingNewlinesToSkip = 4;
			int bytesRead = 1;
			while(bytesRead > 0) {
				byte [] buffer = new byte[10];
				bytesRead = input.read(buffer);
				if (!isHeaderParsed) {
					int nlPos = -1;
					for (int i = 0; i < bytesRead; i++) {
						header.append(Character.toChars(buffer[i]));
						if (buffer[i] == 0x0A) {
							remainingNewlinesToSkip--;
							nlPos = i;
							if (remainingNewlinesToSkip == 0) {
								break;
							}
						}
					}
					if (remainingNewlinesToSkip == 0) {
						isHeaderParsed = true;
						
						if (outputHeaderAlso) {
							output.write(header.toString().getBytes());
						}
						output.flush();
						output = new InflaterOutputStream(new BufferedOutputStream(outputStream));
						
						if (nlPos + 1 == bytesRead) {
							continue;
						} else {
							buffer = Arrays.copyOfRange(buffer, nlPos + 1, bytesRead);
							bytesRead -= nlPos + 1;
						}
					} else {
						continue;
					}
				}
				if (bytesRead > 0) {
					output.write(buffer, 0, bytesRead);
				}
			}
		} finally {
			if (input != null)
				input.close();
			if (output != null)
				output.close();
		}
		return header.toString();
	}
	
	public static void encodeObjectsInv(InputStream inputStream, OutputStream outputStream, String header) throws IOException {
		BufferedReader input = null;
		OutputStream output = null;
		try {
			input = new BufferedReader(new InputStreamReader(inputStream, "UTF-8"));
			output = new BufferedOutputStream(outputStream);
			
			boolean isHeaderParsed = header != null;
			int remainingNewlinesToSkip = 4;
			String linesRead;
			do {
				if (header != null) {
					linesRead = header;
					if (linesRead.endsWith(NL)) {
						linesRead = linesRead.substring(0, linesRead.length() - 1);
					}
				} else {
					linesRead = input.readLine();
				}
				if (linesRead != null) {
					if (!isHeaderParsed) {
						remainingNewlinesToSkip--;
					}
					
					linesRead = linesRead + NL;
					output.write(linesRead.getBytes("UTF-8"));
					
					if ((!isHeaderParsed && remainingNewlinesToSkip == 0) || header != null) {
						output.flush();
						output = new DeflaterOutputStream(new BufferedOutputStream(outputStream));
						isHeaderParsed = true;
						header = null;
					}
				}
			} while (linesRead != null);
		} finally {
			if (input != null)
				input.close();
			if (output != null)
				output.close();
		}
	}
	
	public static void encodeTxtToObjectsInv(String txtFilePath, OutputStream outputStream) throws IOException {
		encodeObjectsInv(new FileInputStream(txtFilePath), outputStream, null);
	}

}
