//Michael Bharrat 144001727
//Syed Zaidi 145000204
//Aaron Lukaszewics 088005488

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.nio.BufferOverflowException;
import java.net.ConnectException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.concurrent.BlockingQueue;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.logging.Logger;

public class Peer extends Thread{

	private static final Logger logger = Logger.getLogger(Peer.class.getName());
	
	private static final String[] message_types = {
        "keep_alive", "choke", "unchoke", "interested", "not_interested", "have", "bitfield", "request", "piece", "cancel"
	};
	
    //client
	RUBTClient client;
    
    //client choking peer?
	boolean amChoking = true;
	
    //client interested in peer?
	boolean amInterested = false;
	
    //peer choke client?
	boolean peerChoking = true;

    //peer interested in client?
	boolean peerInterested = false;

    //hash in metainfo file
	byte[] infoHash;
	
    //peer info obtain. from tracker request
	volatile String peerId;
	String peerIp;
	int peerPort;
	
	//sockets/streams
	Socket socket = null;
	DataInputStream socketInput = null;
	DataOutputStream socketOutput = null;
	
    //handshake msg
	byte[] handshakeMessage;
	
    //keep track if unchoke interest
	boolean sentGreetings = false;
	
    //pieces peer has
	volatile boolean[] peerHaveArray;
	
    //size of array
	int peerHaveArraySize = 0;
	
    //% of file peer has
	int percentPeerHas = 0;
	
    //bitfield of pieces client has
	Message clientBitfield;
	
    //bitfield peer sent to client
	byte[] peerBitfield = null;
	
    //whether client has sent bitfield to peer or not
	boolean sentBitfield = false;
	
    //number of pieces downloaded from peer
	int downloadedFromPeer = 0;
	
    //number pieces peer has and client wants
	int wantFromPeer = 0;

    //request queue
	volatile BlockingQueue<Message> requestSendQueue;
	
    //full of request messages that have been sent
	BlockingQueue<Message> requestedQueue;
	
    //queue for sending have msgs
	volatile BlockingQueue<Message> haveSendQueue;
	
    //number of requests sent
	int requestsSent;
	
    //max number of requests sent
	final int MAX_REQUESTS = 10;
	
    //queue of pieces waiting to be sent
	BlockingQueue<Message> pieceSendQueue;
	
    //pieces sent to a peer
	int[] peerSendArray;

    //if sending peer more times than max -> choke
	final int MAX_SEND_LIMIT = 2;

    //upload limit of peer
	int peer_upload_base;
    
	final long MAX_THROTTLE_INTERVAL = 1000;
	
    //amount of bytes since last interval
	int bytes_sent = 0;
	
    //amount of bytes since last interval
	int bytes_received = 0;

    //last time reset was performed
	long lastThrottleReset;
	
    //upload rate
	int uploadRate = 0;
	
    //download rate
	int downloadRate = 0;
	
    //total bytes uploaded to peer
	int bytesToPeer = 0;
	
    //total bytes downloaded from peer
	int bytesFromPeer = 0;
	
	int fileBytesUploaded = 0;
	int fileBytesDownloaded = 0;
	
    //bad torrent controller?
	boolean badTorrentController = true;
	
	//is peer seed?
	boolean isSeed = false;
	
	boolean isRandomPeer = false;
	
	//sending keep alives to peer
	volatile long clientLastMessage;
	
	//keep connection alive with peer
	volatile long peerLastMessage;
	
	boolean writing = false;
	
	volatile long MAX_KEEPALIVE_INTERVAL = 110000;
	
	//how long client should take to try to pair with peer
	final int CONNECTION_TIMEOUT = 10000;
	
	//attempt made to connect with peer?
	boolean isAttempted = false;
	
    //number of times client tried to connect to peer
	int connectionAttempts = 0;
	
	boolean isActive = false;
	

	boolean canQuit = false;
	
	//done with peer?
	boolean done = false;
	
	boolean RUN = true;
	
//constructor of peer class
	public Peer(String pid, String pip, int pport, Socket sock, RUBTClient c) {
		
		//Initialize the new Peer object
		this.client = c;
		this.peerId = pid;
		this.peerIp = pip;
		this.peerPort = pport;
		this.infoHash = client.torrentInfo.info_hash.array();
		this.handshakeMessage = createHandshake();
		this.peerHaveArray = new boolean[client.numOfPieces];
		this.peerSendArray = new int[client.numOfPieces];
		this.requestSendQueue = new LinkedBlockingQueue<Message>(client.numOfPieces);
		this.requestedQueue = new LinkedBlockingQueue<Message>(client.numOfPieces);
		this.pieceSendQueue = new LinkedBlockingQueue<Message>(client.numOfPieces);
		this.haveSendQueue = new LinkedBlockingQueue<Message>(client.numOfPieces);
		
		// Set up the bitfield
		this.clientBitfield = Message.createBitfield(client);
		
		if (this.peerId == null) {
			this.isRandomPeer = true;
			this.socket = sock;
		}
		
	}
	
	
	//thread for peer object
	public void run() {
        	
		this.client.numOfAttemptedConnections++;
		
           if (this.socketInput == null) this.connect();
		    // Send bitfield
		    if (!this.sentBitfield && this.clientBitfield != null) {
		     	this.sendMessage(this.clientBitfield);
		      	this.sentBitfield = true;
		    }
		    
		    this.clientLastMessage = System.currentTimeMillis();
		    this.peerLastMessage = System.currentTimeMillis();
		    this.lastThrottleReset = System.currentTimeMillis();
		    
		    Timer timer = new Timer();
		    TimerTask timerTask = new TimerTask() {
            
		        public void run() {
	    			//keep alives to peers
	    			if (System.currentTimeMillis() - Peer.this.clientLastMessage > Peer.this.MAX_KEEPALIVE_INTERVAL)
	    				if (Peer.this.amInterested) {
	    					Peer.this.sendMessage(Message.createKeepAlive());
	    					Peer.this.clientLastMessage = System.currentTimeMillis();
	    					Peer.this.peerLastMessage = System.currentTimeMillis();
	    				}
	    			//does peer want connection open?
	    			if (!Peer.this.amInterested && System.currentTimeMillis() - Peer.this.peerLastMessage > Peer.this.MAX_KEEPALIVE_INTERVAL + 25000) {
	    				Peer.this.shutdown();
	    			}
	    			
	    			Peer.this.canQuit = true;
	    		}
	       	};
	        timer.schedule(timerTask, this.MAX_KEEPALIVE_INTERVAL, this.MAX_KEEPALIVE_INTERVAL);
	        
	        while (this.RUN) {
	        	// Reset throttle
	        	if (System.currentTimeMillis() - this.lastThrottleReset > this.MAX_THROTTLE_INTERVAL) {
	        		if (!this.amChoking) {
	        			this.downloadRate = (this.downloadRate + this.bytes_received)/2;
	        			this.uploadRate = (this.uploadRate + this.bytes_received)/2;
	        		}
	        		this.bytes_received = 0;
	        		this.bytes_sent = 0;
	        		this.lastThrottleReset = System.currentTimeMillis();
	        	}
	        	// Set the upload rate
	        	if (this.downloadedFromPeer != 0)
	        		this.peer_upload_base = (int)(((double)this.downloadedFromPeer / this.wantFromPeer)*this.client.UPLOAD_LIMIT) + this.client.UPLOAD_LOWER_LIMIT;
	        	else
	        		this.peer_upload_base = this.client.UPLOAD_LOWER_LIMIT;
	        	// Read the incoming message if within throttle
	        	if (this.bytes_received < this.client.DOWNLOAD_LIMIT) {
					try {
						Message.dealWithMessage(this);
					} catch (IOException e) {
						logger.info("IO exception (in peer class, dealWithMessage()) from Peer "
								+ "ID : [ " + this.peerId + "] with IP : [ " + this.peerIp
								+ " ].");
						this.shutdown();
					}
	        	}
	        	// Reset throttle
	        	if (System.currentTimeMillis() - this.lastThrottleReset > this.MAX_THROTTLE_INTERVAL) {
	        		this.bytes_received = 0;
	        		this.bytes_sent = 0;
	        		this.lastThrottleReset = System.currentTimeMillis();
	        	}
				if (this.canQuit && this.requestSendQueue.isEmpty() && this.requestedQueue.isEmpty()) {
					if (!this.done) {
						logger.info("All pieces downloaded from Peer " 
							+ "ID : [ " + this.peerId + "] with IP : [ " + this.peerIp
							+ " ].");
						if (!this.client.amSeeding) this.sendMessage(Message.createNotInterested());
						this.done = true;
					}
					if (!this.peerInterested) this.shutdown();
				}
	        	// Send requests to the peer, MAX_REQUESTS out at one time
				while (!this.amChoking && !this.peerChoking && this.requestsSent < this.MAX_REQUESTS && !this.requestSendQueue.isEmpty()) {
					Message message = this.requestSendQueue.poll();
					if(client.havePieces[message.ipay[0]]){
						System.out.println("CLIENT ALREADY HAS THIS PIECE");
						continue;
					}
					if (!this.requestedQueue.contains(message)) this.requestedQueue.add(message);
					this.sendMessage(message);
					this.requestsSent++;
				}
				// Send pieces from requests
				while (!this.amChoking && this.bytes_sent < this.peer_upload_base && !this.pieceSendQueue.isEmpty()) {
					try {
						this.sendMessage(this.pieceSendQueue.take());
					} catch (InterruptedException e) {
						e.printStackTrace();
					}
				}
				// Send have messages
				while (!this.haveSendQueue.isEmpty()) {
					try {
						this.sendMessage(this.haveSendQueue.take());
					} catch (InterruptedException e) {
						e.printStackTrace();
					}
				}
					
	        }
	        
	        timer.cancel();
	        
	        logger.info("End of thread with Peer " 
					+ "ID : [ " + this.peerId + "] with IP : [ " + this.peerIp
					+ " ].");
	        
	        this.client.numOfAttemptedConnections--;

	        return;
	}

    //sets connection with peer
	public void connect() {
		try {
			this.isAttempted = true;
			if (!this.isRandomPeer) {
				this.socket = new Socket();
				try {
					this.socket.connect(new InetSocketAddress(peerIp, peerPort), CONNECTION_TIMEOUT);
				} catch (SocketTimeoutException e) {
					logger.info("Connection timed out with Peer "
							+ "ID : [ " + this.peerId + "] with IP : [ " + this.peerIp
							+ " ].");
					this.RUN = false;
					return;
				}
			} else {
				logger.info("Incoming connection from IP : [ " + this.peerIp + " ].");
			}
			if (this.socket == null) System.out.println("socket is null");
			this.socketInput = new DataInputStream(new BufferedInputStream(socket.getInputStream()));
			this.socketOutput = new DataOutputStream(new BufferedOutputStream(socket.getOutputStream()));
			logger.info("Connection established with Peer " 
					+ "ID : [ " + this.peerId + "] with IP : [ " + this.peerIp
					+ " ].");
	        if (this.isRandomPeer) {
	        	this.receiveHandshake();
	        	this.sendHandshake();
	        } else {
	        	this.sendHandshake();
	        	this.receiveHandshake();
	        }
	        this.isActive = true;
	        this.client.neighboring_peers.add(this);
	        this.client.choked_peers.add(this);
	        this.client.numOfActivePeers++;
		} catch (ConnectException e) {
			logger.info("Unsuccessful connection with Peer " 
					+ "ID : [ " + this.peerId + "] with IP : [ " + this.peerIp
					+ " ]. UnknownHostException.");
			this.RUN = false;
		} catch (UnknownHostException e) {
			logger.info("Unsuccessful connection with Peer " 
					+ "ID : [ " + this.peerId + "] with IP : [ " + this.peerIp
					+ " ]. UnknownHostException.");
			this.RUN = false;
		} catch (IOException e) {
			logger.info("Unsuccessful connection with Peer " 
					+ "ID : [ " + this.peerId + "] with IP : [ " + this.peerIp
					+ " ]. IOException.");
			this.RUN = false;
		}
	}
    //closes connection with peer
	public void disconnect() {
		try {
			if (this.socket != null) this.socket.close();
			logger.info("Disconnected from Peer "
					+ "ID : [ " + this.peerId + "] with IP : [ " + this.peerIp
					+ " ].");
		} catch (IOException e) {
			logger.info(e.getMessage() + "in Peer "
					+ "ID : [ " + this.peerId + "] with IP : [ " + this.peerIp
					+ " ].");
			System.err.println(e);
		}
	}
    //closes connection with peer and ends thread
	public void shutdown() {
		if (this.isAttempted) {
			
			this.connectionAttempts++;
                if (this.connectionAttempts >= this.client.MAX_CONNECTION_ATTEMPTS) {
				this.client.numOfActivePeers--;
				this.client.neighboring_peers.remove(this);
				this.client.bad_peers.add(this.peerId);
				logger.info("Peer ID : [ " + this.peerId + " ] has been added to the list of bad peers.");
			}
			this.isAttempted = false;
		}
		if (!this.socket.isClosed())
			this.disconnect();
		this.RUN = false;
	}
//shake hands with peer
	public byte[] createHandshake() {
		byte[] handshakeMessage = new byte[68];
		try {
			ByteBuffer byteBuffer = ByteBuffer.wrap(handshakeMessage);
			byteBuffer.put((byte) 19);
			byteBuffer.put("BitTorrent protocol".getBytes());
			byteBuffer.put(new byte[8]);
			byteBuffer.put(this.infoHash);
			byteBuffer.put(this.client.clientId.getBytes());
			return handshakeMessage;
		} catch (BufferOverflowException e) {
			return null;
		}
	}
//send handshake to peer
	public void sendHandshake() {
		try {
			this.socketOutput.write(this.handshakeMessage);
			this.socketOutput.flush();
	        logger.info("Handshake message was sent to Peer "
					+ "ID : [ " + this.peerId + "] with IP : [ " + this.peerIp
	        		+ " ].");
		} catch (IOException e) {
	        logger.info("A problem occurred while trying to send the handshake message to Peer "
					+ "ID : [ " + this.peerId + "] with IP : [ " + this.peerIp
	        		+ " ].");
			System.err.println(e);
			this.shutdown();
		}
	}
//read handshake from peer
	public void receiveHandshake() {
        byte[] handshakeBytes = new byte[68];
        try {
			this.socketInput.read(handshakeBytes);
			if (Message.verifyHandshake(this.handshakeMessage, handshakeBytes))
				logger.info("The handshake response from Peer "
						+ "ID : [ " + this.peerId + "] with IP : [ " + this.peerIp
						+ " ] was verified.");
			else {
				logger.info("The handshake response from Peer "
						+ "ID : [ " + this.peerId + "] with IP : [ " + this.peerIp
						+ " ] was incorrect. Closing connection...");
				this.shutdown();
			}
			if (this.peerId == null) this.peerId = new String(Arrays.copyOfRange(handshakeBytes, 48, 68));
		} catch (IOException e) {
			System.err.println(e);
			logger.info("Connection dropped. Incorrect handshake sent by Peer "
					+ "ID : [ " + this.peerId + "] with IP : [ " + this.peerIp
					+ " ].");
			this.shutdown();
		}
	}
//sends msg to peer
	public synchronized void sendMessage(Message message) {
		if (this.socketOutput == null || message == null) {
			return;
		}
		if (this.socket.isClosed()) {
			return;
		}
		if(!message_types[message.md + 1].equals("HAVE")){
			System.out.println("Sending {" + message_types[message.md + 1] + "} message to Peer "
					+ "ID : [ " + this.peerId + "] with IP : [ " + this.peerIp
					+ " ].");
		}
				
		
		try {
			this.socketOutput.writeInt(message.lp);
			if (message.md != -1) {
				this.socketOutput.writeByte(message.md);
				if (message.md == 0) {
					this.amChoking = true;
					System.out.println("Started choking Peer "
							+ "ID : [ " + this.peerId + "] with IP : [ " + this.peerIp
							+ " ].");
				}
				if (message.md == 1) {
					this.amChoking = false;
					System.out.println("Unchoked Peer "
							+ "ID : [ " + this.peerId + "] with IP : [ " + this.peerIp
							+ " ].");
				}
				if (message.md == 2) this.amInterested = true;
				if (message.md == 3) this.amInterested = false;
			}
			if (message.ipay != null) {
				int[] tempArray = message.ipay;
				for (int i = 0; i < tempArray.length; i++)
					this.socketOutput.writeInt(tempArray[i]);
			}
			if (message.bpay != null) {
				byte[] tempArray = message.bpay;
				for (int i = 0; i < tempArray.length; i++)
					this.socketOutput.writeByte(tempArray[i]);
			}
			this.socketOutput.flush();
			if (message.md == 7) {
				this.peerSendArray[message.ipay[0]]++;
                    if (this.peerSendArray[message.ipay[0]] > this.MAX_SEND_LIMIT) {
					this.peerSendArray[message.ipay[0]] = 0;
					this.sendMessage(Message.createChoke());
					this.bytesToPeer += message.bpay.length;
				}
			}
			this.bytes_sent += message.lp;
			this.clientLastMessage = System.currentTimeMillis();
			this.peerLastMessage = System.currentTimeMillis();
			if(message_types[message.md + 1].equals("PIECE")){
				System.out.println("\nSENT A PIECE\n");
				this.client.bytesUploaded += message.bpay.length;
				this.fileBytesDownloaded += message.bpay.length;
			}
			
		} catch (IOException e) {
			System.out.println(e.toString());
			logger.info("A problem occurrred while trying to send a {" + message_types[message.md + 1] + "} message to Peer " 
					+ "ID : [ " + this.peerId + "] with IP : [ " + this.peerIp
					+ " ].");
			this.shutdown();
		}
	}
}
