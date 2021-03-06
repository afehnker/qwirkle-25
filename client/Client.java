package client;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.InetAddress;
import java.net.Socket;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

import shared.*;

public class Client extends Thread {

	/**
	 * Starts the client application.
	 * @param args
     */
	public static void main(String[] args) {
		Client client = new Client();
		client.start();
	}

	private String clientName;
	private Socket sock = null;
	private InetAddress hostname;
	private int port;
	private BufferedReader in;
	private BufferedWriter out;
	@SuppressWarnings("unused")
	private String[] options;
	private ClientGame game = null;
	private View view;
	private int aantal;
	private boolean registerSuccesfull = false;
	private boolean computerPlayerBool;
	private Strategy strategy;
	private List<String> players;
	private Player you;
	private String lastMove;
	private String lastline;
	private String lastSentLine;

	/**
	 * Initializes the client by requesting the server information
	 * and asking for a username and information on what type of game to
	 * start.
	 */
	public Client() {
		this.view = new View(this);
		players = new LinkedList<String>();
		init();
		while (!registerSuccesfull) {
			login();
		}
		startGame();
	}

	/**
	 * Asks the user to type the hostname of the server and then the portnumber of the server.
	 */
	private void init() {
		sock = null;
		hostname = null;
		while (hostname == null) {
			try {
				hostname = view.getHostName();
			} catch (UnknownHostException e) {
				view.print("Invalid hostname");
			}
		}
		port = view.getPort();
	}

	/**
	 * Asks the user to type his/her username and starts the streams of input 
	 * and output from and to the server. If the username is already taken the 
	 * user is asked to type a new one.
	 */
	private void login() {
		clientName = view.getClientName();
		while (sock == null) {
			try {
				sock = new Socket(hostname, port);
			} catch (IOException e) {
				view.print("Could not connect to server");
			}
		}
		try {
			in = new BufferedReader(new InputStreamReader(sock.getInputStream()));
			out = new BufferedWriter(new OutputStreamWriter(sock.getOutputStream()));
		} catch (IOException e) {
			e.printStackTrace();
		}
		sendMessage(Protocol.REGISTER + Protocol.SPLIT + getClientName());
		String input = null;
		input = readString();
		if (input != null) {
			while (!input.startsWith(Protocol.ERROR) && !input.startsWith(Protocol.ACKNOWLEDGE)) {
				input = readString();
			}
			System.out.println(input);
			String[] inputArray = input.split(Protocol.SPLIT);
			if (inputArray[0].equals(Protocol.ACKNOWLEDGE)) {
				registerSuccesfull = true;
			} else if (inputArray[0].equals(Protocol.ERROR)) {
				if (inputArray.length == 1) {
					System.out.println("wrong error code");
				} else if (!inputArray[1].equals(Protocol.ErrorCode.INVALIDNAME)) {
					System.out.println(input);
				}
			}
		}
		if (!registerSuccesfull) {
			view.print("username taken");
			sock = null;
		}
	}

	/**
	 * Asks the user if he wants to play as humanplayer or let a computerplayer
	 * play and if he chooses a computerplayer then he can choose a strategy and
	 * enter the thinking time for the computer player. Then he
	 * can choose with how many players he wants to start a game.
	 */
	private void startGame() {
		this.computerPlayerBool = view.askHumanOrComputerPlayer();
		if (computerPlayerBool == true) {
			int playTime = view.getPlayTimeFromInput();
			this.strategy = view.getStrategyFromInput(playTime);
			
		}
		this.aantal = view.startGame();
		join(aantal);
	}

	/**
	 * Sets the field "you " in client with the player in the clientgame that has
	 * the name of the player that plays on this client.
	 * 
	 * @param player
	 */
	public void setYou(Player player) {
		you = player;
	}

	/**
	 * Get the boolean that determines of this client plays as computerplayer or
	 * as humanplayer.
	 * 
	 * @return true if this client plays as computerplayer, false if this client
	 * 			plays as humanplayer.
	 */
	public boolean getComputerPlayerBool() {
		return computerPlayerBool;
	}

	/**
	 * Get the strategy of the computerplayer of this client.
	 * 
	 * @return the strategy
	 */
	public Strategy getStrategy() {
		return strategy;
	}

	/**
	 * As long as there is input from the server, after the client started this run method,
	 * the client will analyze which command came through the input and responds accordingly.
	 * If no command came through the client will shutdown.
	 */
	public void run() {
		String input = null;
		try {
			while ((input = readString()) != null) {
				lastline = input;
				String[] inputArray = input.split(Protocol.SPLIT);
				if (inputArray[0].equals(Protocol.ERROR)) {
					if (inputArray.length == 1) {
						System.out.println("error");
						view.print("Geen foutcode meegegeven foei foei foei");
					} else if (inputArray[1].equals("0")) {
						view.print("Fout commando: 0");
						System.out.println("You sent: " + lastSentLine);
					} else if (inputArray[1].equals("1")) {
						view.print("Foute beurt: 1");
						System.out.println(game.getBoard());
						System.out.println("stones: " + game.getCurrentPlayer().getStones());
						System.out.println("Move: " + lastMove);
						for (PossibleMove p : game.getBoard().getPossibleMoves().values()) {
							System.out.println("Possible Move: Position: " + p.getPosition() 
											+ " Column: " + p.getColumn() + " Row: " + p.getRow());
						}
					} else if (inputArray[1].equals("2")) {
						view.print("Niet unieke naam of onjuiste naam: 2");
					} else if (inputArray[1].equals("3")) {
						view.print("Speler disconnected: 3");
						this.playagain();
					} else if (inputArray[1].equals("4")) {
						view.print("Speler heeft functie niet: 4");
					}

				} else if (inputArray[0].equals(Protocol.PLAYERS)) {
					if (inputArray.length >= 2) {
						players = new ArrayList<String>();
						for (int i = 1; i < inputArray.length; i++) {
							players.add(inputArray[i]);
						}
					}
				} else if (inputArray[0].equals(Protocol.JOINLOBBY)) {
					if (inputArray.length >= 2) {
						String message = Protocol.DELIMITER
										+ "Player " + inputArray[1] + " joined the room"
										+ Protocol.DELIMITER;
						view.print(message);
					}
				} else if (inputArray[0].equals(Protocol.START)) {
					if (inputArray.length >= 2) {
						if (input.contains(clientName)) {
							initGame(inputArray);
						}
					} else {
						throw new InvalidCommandException("Server starts my game with the"
								+ " wrong amount of players.");
					}
				}
				if (game != null) {
					if (inputArray[0].equals(Protocol.PLACED)) {
						if (inputArray.length >= 5) {
							String[] newArray = new String[inputArray.length - 2];
							for (int i = 2; i < inputArray.length; i++) {
								newArray[i - 2] = inputArray[i];
							}
							placed(newArray);
						} else {
							throw new InvalidCommandException();
						}
					} else if (inputArray[0].equals(Protocol.NEWSTONES)) {
						List<Stone> stones = null;
						try {
							stones = Protocol.stringToStoneList(inputArray);
							game.giveStones(stones, clientName);
						} catch (InvalidStoneException e) {
							throw new InvalidCommandException();
						} catch (InvalidMoveException e) {
							throw new InvalidCommandException();
						}
					} else if (inputArray[0].equals(Protocol.TRADED)) {
						view.print("Speler " + inputArray[1] + " traded " 
										+ inputArray[2] + " stones.");
					} else if (inputArray[0].equals(Protocol.TURN)) {
						if (inputArray.length == 2) {
							turn(inputArray);
						} else {
							throw new InvalidCommandException();
						}
					} else if (inputArray[0].equals(Protocol.ENDGAME)) {
                        endgame();
                    }
				}
			}
		} catch (InvalidCommandException e) {
			e.printStackTrace();
			serverBroken();
		} catch (GameNotEndedException e) {
			serverBroken();
		}
		shutdown();
	}

	/**
	 * If the game ended on the server this method is called from the run method.
	 * This method shows on the view which player has won and all points of all 
	 * the players, then asks if the client wants to play another game.
	 * 
	 * @throws GameNotEndedException
	 */
	private void endgame() throws GameNotEndedException {
		game.getCurrentPlayer().addPoints(6);
		Player winner = game.getWinner();
		if (winner.getName().equals(you.getName())) {
			view.print("YOU HAVE WON CONGRATS JEEEEEEEEEEEJ!!!!!!\nWil je nu een koekje?\n");
		}
		String message = "Game ended, player " + winner.getName() 
						+ " has won.\n" + "with " + winner.getPoints()
						+ " points.";
		view.print(message + game.toString());
		game = null;
		playagain();
	}

	/**
	 * asks the user if he wants to play another game,
	 * if yes, then call startGame().
	 * if no, shutDown();
	 */
	private void playagain() {
		String playagain = view.readString("Do you want to play another game? y/n: ");
		if (playagain.equals("y")) {
			startGame();
		} else {
			shutdown();
		}

	}

	/**
	 * This method handles the placed command from the server, it makes the move on the board.
	 * then checks if the server calculated the points correctly.
	 * if everything went okay the points are added to the current player and the
	 * game is printed to the view.
	 * If the move received is incorrect or the points are incorrectly calculated.
	 * An InvalidCommandExcception is thrown.
	 *
	 * @param inputArray
	 * @throws InvalidCommandException
	 */
	private void placed(String[] inputArray) throws InvalidCommandException {
		List<Stone> stones = Protocol.stringToPlacedStoneList(inputArray);
		List<Position> positions = Protocol.stringToPlacePositionList(inputArray);
		try {
			game.makeMove(positions, stones);
			if (game.getBoard().calculatePoints(stones, positions)
							== Integer.parseInt(inputArray[0])) {
				game.addPoints(inputArray[0]);
				view.print(game.toString());
			} else {
				serverBroken();
			}
		} catch (InvalidMoveException e) {
			throw new InvalidCommandException();
		} catch (NumberFormatException e) {
			throw new InvalidCommandException();
		}
	}

	/**
	 * Handles the turn command.
	 * the received player is made the currentplayer.
	 * The move count is incremented. And either the user makes a move or
	 * a message is printed that the program is waiting for another player to make a move.
	 *
	 * @param inputArray
	 * @throws InvalidCommandException
	 */
	//@ requires inputArray.length == 2;
	private void turn(String[] inputArray) throws InvalidCommandException {
		game.setCurrentPlayer(inputArray[1]);
		game.incMoveCount();
		if (inputArray[1].equals(clientName)) {
			System.out.println("current player making move");
			game.getCurrentPlayer().makeMove();
		} else {
			view.print("waiting for " + inputArray[1] + " to make a move");
		}
	}

	/**
	 * Handles the start command.
	 * Creates a clientgame with the players the server sends to this method,
	 *
	 * @param inputArray
	 */
	private void initGame(String[] inputArray) {
		String[] newPlayers = new String[inputArray.length - 1];
		for (int i = 1; i < inputArray.length; i++) {
			newPlayers[i - 1] = inputArray[i];
		}
		this.game = new ClientGame(newPlayers, this);
	}

	/**
	 * Gets the game that is played by this client.
	 * 
	 * @return the game.
	 */
	public ClientGame getGame() {
		return game;
	}

	/**
	 * Gets the view that is used.
	 * 
	 * @return the view
	 */
	public View getView() {
		return view;
	}

	/**
	 * Sends the specified message to the server.
	 * 
	 * @param msg
	 */
	private void sendMessage(String msg) {
		lastSentLine = msg;
		try {
			out.write(msg);
			out.newLine();
			out.flush();
		} catch (IOException e) {
			shutdown();
		}
	}

	/**
	 * closes the socket connection.
	 */
	private void shutdown() {
		view.print("Closing socket connection...");
		try {
			in.close();
			out.close();
			sock.close();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	/**
	 * returns the clients name.
	 */
	public String getClientName() {
		return clientName;
	}

	/**
	 * receives a string from the server.
	 *
	 * @return input from server
	 */
	private String readString() {
		String line = null;
		try {
			line = in.readLine();
		} catch (IOException e) {
		}
		return line;
	}

	/**
	 * Sends the place command.
	 * Sends to the server the stones that the clientplayer placed and where
	 * those stones were placed.
	 * In the format specified in the protocol.
	 * 
	 * @param stones
	 */
	public void place(List<Stone> stones) {
		String msg = Protocol.PLACE;
		for (Stone s : stones) {
			msg = msg + Protocol.SPLIT + s.toUsableString() 
					+ Protocol.SPLIT + s.getPosition().toUsableString();
		}
		sendMessage(msg);
		lastMove = msg;
	}

	/**
	 * Sends the trade command.
	 * Sends to the server the stones the clientplayer traded.
	 * In the format specified in the protocol.
	 *
	 * @param stones
	 */
	public void trade(List<Stone> stones) {
		String msg = Protocol.TRADE;
		for (int i = 0; i < stones.size(); i++) {
			Stone s = stones.get(i);
			msg = msg + Protocol.SPLIT + s.toUsableString();
		}
		sendMessage(msg);
	}

	/**
	 * Sends the join command.
	 * Sends to the server that the clientplayer wants to join a game
	 * with the specified amount of players.
	 * 
	 * @param amount
	 */
	private void join(int amount) {
		sendMessage(Protocol.JOINAANTAL + Protocol.SPLIT + amount);
		view.print("waiting for other players...");
	}

	/**
	 * Asks the server which players are online.
	 */
	public void askPlayers() {
		sendMessage(Protocol.WHICHPLAYERS);
	}

	/**
	 * If the server gives input that is not according to the protocol the
	 * client shuts down and the view prints a message indicating that the server is broken.
	 */
	private void serverBroken() {
		System.out.println("Server is broken. OKDOEI!");
		System.out.println("command: " + lastline);
		shutdown();
	}
}
