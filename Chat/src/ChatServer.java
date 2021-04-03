import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.HashSet;
import java.util.Timer;
import java.util.TimerTask;

public class ChatServer extends Thread {
	private static boolean debug;
	private static boolean active = true;
	private ObjectInputStream oin;
	private ObjectOutputStream oout;
	private static ServerSocket serverSock;

	private static HashSet<String> nicknames;
	private static int numClients = 0;
	private static KillSwitch kswitch;
	private static Timer timer;
	private static int numThreads = 0;
	// 0 = Lobby
	// 1 = room 1
	// 2 = room 2
	private static String[][] room;
	private static String[] roomNames;
	private static int[] messageCounter = { 0, 0, 0 };// messsge counter

	// Connection variables
	private Socket sock;
	private String clientNick;
	private int clientChannel = 0; // Start in Lobby
	private int clientLastMessage = 0;

	private static Object lock = new Object();

	public ChatServer() throws IOException {
		if (debug) {
			System.out.println("Attempting to retrieve a client");
		}
		getClient();

	}

	private ChatServer(int i) {

	}

	private void getClient() throws IOException {
		sock = serverSock.accept();
		if (debug) {
			System.out.println("Client found!");
		}

		this.oout = new ObjectOutputStream(sock.getOutputStream());
		this.oin = new ObjectInputStream(sock.getInputStream());
	}

	public static void main(String[] args) {

		if (args.length < 4) {
			System.out.println("javac ChatServer -p <port#> -d <debug level>");
			System.exit(1);
		}
		int port = Integer.parseInt(args[1]);
		int debugNum = Integer.parseInt(args[3]);
		if (debugNum == 1) {
			debug = true;
		} else {
			debug = false;
		}

		startKillSwitch();
		// resetKillSwitch();
		// TODO make shutdown hook
		Thread hook = new Thread() {
			public void run() {
				System.out.println("Kill signal found. Terminating");
				active = false;
			}
		};
		Runtime.getRuntime().addShutdownHook(hook);

		nicknames = new HashSet<String>();
		room = new String[3][100];
		roomNames = new String[3];
		roomNames[0] = "Lobby";
		roomNames[1] = "Alpha";
		roomNames[2] = "Beta";

		try {
			serverSock = new ServerSocket(port);
			System.out.println("Server starting");
			if (debug) {
				System.out.println("debug mode active");
			}
			while (active) {
				if (numThreads < 4) {
					Thread connection = new ChatServer();

					connection.start();
					synchronized (lock) {
						numClients++;
						numThreads++;
					}

				}
			}
			serverSock.close();
			System.exit(0);
		} catch (IOException e) {

			e.printStackTrace();
		}

	}

	// Send message to client welcoming them to the server
	// request a nickname before allowing further access to anything
	private void confirmConnect() throws IOException, ClassNotFoundException {
		resetKillSwitch();
		clientLastMessage = 0;
		clientChannel = 0;
		String[] reply = { "Welcome to the Server", "Please, select a nickname with /nick" };
		Responce responce = new Responce(reply);
		// Welcome client to server
		if (debug) {
			System.out.println("Welcoming client to server.");
		}
		oout.writeObject(responce);

		boolean setNick = false;

		// this should stop this code from running while any other code
		// uses critical sections
		synchronized (lock) {
			while (!setNick) {
				Command client = (Command) oin.readObject();
				String msg = client.getMsg();
				if (msg.startsWith("/nick")) {
					//SETNICK writes a responce to the user
					setNick = setNickname(msg);
					continue;
				}
				// if Nickname succesfuly set
				// send client to lobby
			

			}
			String str = clientNick + " has connected to server!";
			// inform room users that client has joined
			addMsg(str);
			//Bring new user up to speed
			responce = getMessages();

			// track last message
			clientLastMessage = messageCounter[0];
			oout.writeObject(responce);

		}
	}

	// await client request
	public void listen() throws ClassNotFoundException, IOException {
		Command client = (Command) oin.readObject();
		String msg = client.getMsg();
		// reset kill switch
		resetKillSwitch();
		// command
		if (msg.startsWith("/")) {
			if (msg.startsWith("/nick")) {

				String[] userMsg = msg.split(" ");
				if (userMsg.length < 2) {
					Responce responce = new Responce("You must select a Nickname to use that command");
					responce.setSuccess(false);
					oout.writeObject(responce);
				} else {
					// edge case, they already have this nickname
					if (clientNick.equals(userMsg[1])) {
						Responce responce = new Responce("You already have that nickname");
						responce.setSuccess(false);
						oout.writeObject(responce);
					} else {
						synchronized (lock) {
							String oldNick = clientNick;
							boolean success = setNickname(msg);
							// inform all users tahat a nickname has been
							// changed.
							String update = oldNick + " has changed to " + userMsg[1];
							addMsg(update);
							if (success) {
								// only if new nickname approved
								// remove previous nickname
								nicknames.remove(oldNick);

							}
						}
					}
				}

			} else if (msg.startsWith("/list")) {
				// Generates a list of all the rooms and usernames then
				// returns it to the client
				Responce responce = null;
				synchronized (lock) {
					responce = getList();
				}
				responce.setSuccess(false);
				oout.writeObject(responce);
			} else if (msg.startsWith("/help")) {
				// IF client asks for help then return the set of
				// Usable commands to user
				String[] help = { "List of commands:", "/nick - set your nickname", "/leave - return to lobby",
						"/stats - view number of messages and number of connected users",
						"/join <channel> - connects you to specified channel",
						"/list - names all connected users and live rooms",
						"/quit - disconnectes user from aplication" };
				Responce responce = new Responce(help);
				oout.writeObject(responce);

			} else if (msg.startsWith("/stats")) {
				synchronized (lock) {
					Responce responce = getStats();
					oout.writeObject(responce);
				}

			} else if (msg.startsWith("/join")) {
				
				Responce responce = null;
				String[] strArray = msg.split(" ");
				String room = strArray[1];
				boolean roomChanged = false;
				if (debug) {
					System.out.println(clientNick + " in room " + roomNames[clientChannel]);
				}

				synchronized (lock) {
					for (int i = 0; i < roomNames.length; i++) {
						if (roomNames[i].equals(room)) {
							// change the clients chat room

							clientChannel = i;

							// allow client to recieve all messages
							clientLastMessage = 0;
							roomChanged = true;
							if (debug) {
								System.out.println(clientNick + " moved to room" + roomNames[clientChannel]);
							}
							//String[] str = { "Moved to room: " + roomNames[clientChannel] };
							addMsg(clientNick + " joined the channel.");
							responce = getMessages();
							//responce = new Responce(str);
							oout.writeObject(responce);
						}
					}
					if (!roomChanged) {
						// inform user that room does not exist
						if (debug) {
							System.out.println(clientNick + " tried to move to a non existant room");
						}
						String[] str = { "The room: " + room + " does not exist.", "Please try again" };
						responce = new Responce(str);
						oout.writeObject(responce);
					}
				}

			} else if (msg.startsWith("/leave")) {

				// GOTO lobby
				Responce responce = null;
				if (clientChannel != 0) {
					// send client to lobby
					clientChannel = 0;
					clientLastMessage = 0;
					synchronized (lock) {
						responce = getMessages();
					}
				} else {
					String[] str = { "You are already in the lobby.", "Please, try again." };
					responce = new Responce(str);
					// inform client that they are already in the lobby;
				}
				oout.writeObject(responce);
			} 
			else if (msg.startsWith("/quit")) {
				synchronized (lock) {
					String[] str = { "Goodbye" };
					Responce responce = new Responce(str);
					// inform users that client left
					str[0] = clientNick + " has disconnected.";
					if (debug) {
						System.out.println(clientNick + " is disconnecting");
					}
					nicknames.remove(clientNick);
					clientNick = null;
					numClients--;
					addMsg(str[0]);

					// notify client that it is ok to disconnect
					oout.writeObject(responce);
					oout.flush();
					sock.close();
					
				}
				getClient();
				confirmConnect();
			}
			// incorrect use of command
			else {
				Responce responce = new Responce("Command not recognized, try /help.");
				responce.setSuccess(false);
				oout.writeObject(responce);

			}
			// move to designated channel
		}
		// message
		else {
			synchronized (lock) {
				String newMessage = clientNick + ": " + client.getMsg();
				// if not enough room in chat room, expand chat room
				if (room[clientChannel].length + 1 >= room[clientChannel].length) {
					expandArray(room[clientChannel]);
				}
				// add message to room
				addMsg(newMessage);

				Responce responce = getMessages();

				oout.writeObject(responce);

			}

		}

	}

	private synchronized void addMsg(String newMessage) {
		room[clientChannel][messageCounter[clientChannel]] = newMessage;
		// increment number of messages in channel
		messageCounter[clientChannel] += 1;
	}

	private synchronized Responce getMessages() {
		Responce responce = null;
		String[] chatter;// contains unseen messages from channel
		int pointer = clientLastMessage;

		// only copy messages needed
		int copyLength = messageCounter[clientChannel] - pointer;
		//
		chatter = new String[copyLength];
		// copy nessisary strings to chatter
		// array Copy( sourceArray, sourcePos, destArray, DestPos, length);
		System.arraycopy(room[clientChannel], pointer, chatter, 0, copyLength);
		if (debug) {
			System.out.println("Room messages room: " + clientChannel);
			for (int i = 0; i < messageCounter[clientChannel]; i++) {
				System.out.println(room[clientChannel][i]);
			}
		}
		clientLastMessage = messageCounter[clientChannel] ;
		responce = new Responce(chatter);
		responce.setSuccess(true);

		return responce;
	}

	private Responce getStats() {
		Responce responce;
		int counter = 0;
		for (int i : messageCounter) {
			counter += i;
		}
		String[] stats = new String[numClients + 1];
		stats[0] = "Total Messages: " + counter + "\nTotal users: " + numClients + "\nCllients:";
		counter = 1;
		for (String nick : nicknames) {
			if (counter >= stats.length) {
				expandArray(stats);
			}
			stats[counter] = nick;
			counter += 1;
		}
		responce = new Responce(stats);
		return responce;
	}

	private Responce getList() {
		Responce responce;
		String[] list = new String[numClients + 2 + room.length];
		int count = 0;
		list[count] = "Chat rooms:";
		count++;
		for (String str : roomNames) {
			list[count] = str;
			count++;
		}
		list[count] = "Users:";
		count++;
		for (String str : nicknames) {
			if (count >= list.length) {
				expandArray(list);
			}
			list[count] = str;
			count++;
		}
		responce = new Responce(list);
		return responce;
	}

	/**
	 * Expands the given array to twice its current size and copies all content
	 * to the new array
	 * 
	 * @param array
	 *            - array to be expanded
	 */
	private synchronized void expandArray(String[] array) {
		String[] temp = new String[array.length * 2];
		System.arraycopy(array, 0, temp, array.length, array.length);
		array = temp;

	}

	/**
	 * sets client's nickname
	 * 
	 * @param msg
	 *            - content of the command
	 * @return - true if setting Nickname is successfule, false otherwise
	 * @throws IOException
	 */
	private boolean setNickname(String msg) throws IOException {
		Boolean setNick = false;
		Responce responce;
		String[] reply = new String[1];
		String[] nick = null;
		nick = msg.split(" ", 5);
		if (nick.length != 2) {
			reply[0] = "Incorrect number of arguments, try again";
		} else {
			synchronized (lock) {

				if (nicknames.contains(nick[1])) {
					// nickname already taken
					reply = new String[2];
					reply[0] = "That Nickname is already Taken";
					reply[1] = "Please, choose another with /nick";
				} else {
					System.out.println(nick.toString());
					clientNick = nick[1];
					nicknames.add(nick[1]);
					setNick = true;
					String str = "Nickname set to: " + nick[1];
					reply[0] = str;
					if (debug) {
						System.out.println("Client nickname set to: " + nick[1]);
					}
				}

			}
		}
		// accept or reject nickname
		responce = new Responce(reply);
		responce.setSuccess(setNick);
		oout.writeObject(responce);

		return setNick;

	}

	public void run() {
		try {
			// sets nickname
			// also sends client to lobby
			confirmConnect();
			while (true) {
				listen();
			}

		} catch (ClassNotFoundException e) {
			e.printStackTrace();
		} catch (IOException e) {
			// IF disconencted from client, try to find a new client
			// e.printStackTrace();
			try {
				System.out.println(clientNick +" abruptly disconnected, attempting to get new clients.");
				synchronized (lock) {
					nicknames.remove(clientNick);
					clientNick = null;
					numClients--;
				}
				getClient();
			} catch (IOException e1) {
				// TODO Auto-generated catch block
				e1.printStackTrace();
			}
			run();
		}
	}

	private static void resetKillSwitch() {
		// cancel and purge old timer
		timer.cancel();//
		timer.purge();
		// create new timer
		timer = new Timer();
		// create new timer task
		kswitch = new ChatServer(1).new KillSwitch();

		long period;
		if (debug) {
			period = 600000;
		} else {
			period = 600000;
		}

		System.out.println("Restarting timer, set to " + period + " seconds");
		timer.scheduleAtFixedRate(kswitch, period, period);
	}

	private static void startKillSwitch() {
		// TODO make killswitch timer
		kswitch = new ChatServer(1).new KillSwitch();

		timer = new Timer();
		long period;

		if (debug) {
			// ONLY changed in testing
			period = 600000;
		} else {
			period = 600000;
		}

		System.out.println("Starting Timer, set to " + period + " seconds");
		timer.scheduleAtFixedRate(kswitch, period, period);
	}

	public class KillSwitch extends TimerTask {
		public void run() {
			System.out.println("time up \nshutting down");
			try {
				active = false;
				serverSock.close();
				System.exit(1);
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
	}
}