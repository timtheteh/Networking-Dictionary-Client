import java.lang.System;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.util.Arrays;
import java.io.BufferedReader;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketAddress;
import java.net.SocketTimeoutException;

public class CSdict {
	static final int MAX_LEN = 255;
	static Boolean debugOn = false;

	private static final int PERMITTED_ARGUMENT_COUNT = 1;
	private static String command;
	private static String[] arguments;

	static Socket socket;
	static PrintWriter out;
	static BufferedReader in;

	static String currentDict = "*";

	static boolean isConnected = false;

	public static void main(String[] args) {
		byte cmdString[] = new byte[MAX_LEN];
		// Verify command line arguments

		if (args.length == PERMITTED_ARGUMENT_COUNT) {
			debugOn = args[0].equals("-d");
			if (debugOn) {
				System.out.println("Debugging output enabled");
			} else {
				System.out.println("997 Invalid command line option - Only -d is allowed");
				return;
			}
		} else if (args.length > PERMITTED_ARGUMENT_COUNT) {
			System.out.println("996 Too many command line options - Only -d is allowed");
			return;
		}

		// Example code to read command line input and extract arguments.

		try {
			while (true) {
				Arrays.fill(cmdString, (byte) 0);
				System.out.print("csdict> ");
				System.in.read(cmdString);
				// Convert the command string to ASII
				String inputString = new String(cmdString, "ASCII");

				// Split the string into words
				String[] inputs = inputString.trim().split("( |\t)+");
				// Set the command
				command = inputs[0].toLowerCase().trim();
				// Remainder of the inputs is the arguments.
				arguments = Arrays.copyOfRange(inputs, 1, inputs.length);

				switch (command.toLowerCase()) {

					case "quit":
						quit();
						return;

					case "open":
						open(arguments);
						break;

					case "dict":
						dict();
						break;

					case "close":
						close();
						break;

					case "define":
						// eg. define penguin
						define(arguments);
						break;

					case "set":
						setDict(arguments);
						break;

					case "match":
						match(arguments);
						break;

					case "prefixmatch":
						prefixmatch(arguments);
						break;

					default:
						System.out.println("900 Invalid command.");
				}
			}
		} catch (IOException exception) {
			System.err.println("998 Input error while reading commands, terminating.");
			System.exit(-1);
		}
	}

	public static void open(String[] arguments) {
		if (isConnected) {
			System.out.println("903 Supplied command not expected at this time.");
			return;
		}
		if (arguments.length != 2) {
			System.out.println("901 Incorrect number of arguments.");
			return;
		}

		int port = 0;
		String host = arguments[0];

		try {
			port = Integer.parseInt(arguments[1]);
		} catch (NumberFormatException e) {
			System.out.println("902 Invalid argument.");
			return;
		}

		if (!(arguments[0] instanceof String)) {
			System.out.println("902 Invalid argument.");
		}

		try {
			SocketAddress sockaddr = new InetSocketAddress(host, port);

			socket = new Socket();
			socket.connect(sockaddr, 30000);
			out = new PrintWriter(socket.getOutputStream(), true);
			in = new BufferedReader(new InputStreamReader(socket.getInputStream(), "UTF-8"));
			String fromServer = in.readLine();

			if (fromServer == null) {
				System.out.println("999 Processing error. Timed out while waiting for a response.");
				return;
			}
			if (fromServer.startsWith("220")) {
				isConnected = true;
				printDebugStatementResponse(fromServer);
				return;
			}
		}

		catch (SocketTimeoutException ste) {
			System.out.println("920 Control connection to " + host + " on port " + port + " failed to open.");

		}

		catch (IOException ioe) {
			System.out.println("920 Control connection to " + host + " on port " + port + " failed to open.");

		}
	}

	public static void dict() throws IOException {

		if (!isConnected) {
			System.out.println("903 Supplied command not expected at this time.");
			return;
		}

		try {
			out.println("SHOW DB");
			printDebugStatementRequest("SHOW DB");

			String fromserver;

			while ((fromserver = in.readLine()) != null) {
				if (fromserver.startsWith("110")) {
					printDebugStatementResponse(fromserver);
					continue;
				}
				if (fromserver.startsWith("250")) {
					printDebugStatementResponse(fromserver);
					return;
				}
				System.out.println(fromserver);
			}
		} catch (IOException e) {
			System.out.println("925 Control connection I/O error, closing control connection.");
			socket.close();
		}
	}

	public static void close() throws IOException {
		if (!isConnected) {
			System.out.println("903 Supplied command not expected at this time.");
			return;
		}
		try {
			out.println("QUIT");
			printDebugStatementRequest("QUIT");
			String fromserver = in.readLine();

			if (fromserver.startsWith("221")) {
				printDebugStatementResponse(fromserver);
				socket.close();
				isConnected = false;
				return;
			}
		} catch (IOException e) {
			System.out.println("925 Control connection I/O error, closing control connection.");
			socket.close();
		}
	}

	public static void define(String[] inputs) throws IOException {
		if (!isConnected) {
			System.out.println("903 Supplied command not expected at this time.");
			return;
		}
		if (inputs.length != 1) {
			System.out.println("901 Incorrect number of arguments.");
			return;
		}

		try {
			String command = "DEFINE " + currentDict + " " + inputs[0];
			printDebugStatementRequest(command);
			out.println(command);
			String fromserver;

			while ((fromserver = in.readLine()) != null) {
				if (fromserver.startsWith("250")) {
					printDebugStatementResponse(fromserver);
					return;
				}
				if (fromserver.startsWith("552")) {
					System.out.println("****No definition found****");
					match(inputs);
					return;
				}
				if (fromserver.startsWith("150")){
					printDebugStatementResponse(fromserver);
					continue;
				}
				String line_array[] = fromserver.split("\\r?\\n");
				String word_array[] = line_array[0].split("\\s+");
				if (line_array[0].startsWith("151")) {
					printDebugStatementResponse(line_array[0]);
					String dict_string = String.format("@ %s \"%s\" ", word_array[2],
							line_array[0].substring(4 + 2 + word_array[1].length() + word_array[2].length()).replaceAll("\"", ""));
					System.out.println(dict_string);
				} else {
					System.out.println(fromserver.trim());
				}
			}

		} catch (IOException e) {
			System.out.println("925 Control connection I/O error, closing control connection.");
			socket.close();
		}
	}

	public static void setDict(String[] inputs) {
		if (!isConnected) {
			System.out.println("903 Supplied command not expected at this time.");
			return;
		}
		if (inputs.length != 1) {
			System.out.println("901 Incorrect number of arguments.");
			return;
		}
		currentDict = inputs[0];
	}

	public static void match(String[] inputs) throws IOException {
		if (!isConnected) {
			System.out.println("903 Supplied command not expected at this time.");
			return;
		}
		if (inputs.length != 1) {
			System.out.println("901 Incorrect number of arguments.");
			return;
		}

		try {
			String command = "MATCH *" + " exact " + inputs[0];
			out.println(command);
			printDebugStatementRequest(command);
			String fromserver;
			while ((fromserver = in.readLine()) != null) {
				if (fromserver.startsWith("552")) {
					System.out.println("****No matching word(s) found****");
					return;
				}
				if (fromserver.startsWith("250")) {
					printDebugStatementResponse(fromserver);
					return;
				}
				if (fromserver.startsWith("152")) {
					printDebugStatementResponse(fromserver);
				} else {
					System.out.println(fromserver);
				}
			}
		} catch (IOException e) {
			System.out.println("925 Control connection I/O error, closing control connection.");
			socket.close();
		}
	}

	public static void prefixmatch(String[] inputs) throws IOException {
		if (!isConnected) {
			System.out.println("903 Supplied command not expected at this time.");
			return;
		}
		if (inputs.length != 1) {
			System.out.println("901 Incorrect number of arguments.");
			return;
		}
		try {
			String command = "MATCH *" + " prefix " + inputs[0];
			printDebugStatementRequest(command);
			out.println(command);
			String fromserver;
			while ((fromserver = in.readLine()) != null) {
				if (fromserver.startsWith("552")) {
					System.out.println("****No matching word(s) found****");
					return;
				}
				if (fromserver.startsWith("250")) {
					printDebugStatementResponse(fromserver);
					return;
				}
				System.out.println(fromserver);
			}
		} catch (IOException e) {
			System.out.println("925 Control connection I/O error, closing control connection.");
			socket.close();
		}
	}

	public static void quit() throws IOException {
		if (isConnected) {
			close();
		}
		System.exit(0);
		return;
	}

	public static void printDebugStatementResponse(String message) {
		if (debugOn) {
			System.out.println("<-- " + message);
		}
	}

	public static void printDebugStatementRequest(String message) {
		if (debugOn) {
			System.out.println("> " + message);
		}
	}

}
