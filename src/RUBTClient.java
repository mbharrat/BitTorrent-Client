//Michael Bharrat 144001727
//Syed Zaidi 145000204
//Aaron Lukaszewics 088005488

import java.util.Map;
import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.logging.Logger;
import java.util.ArrayList;
import java.nio.ByteBuffer;
import java.util.Timer;
import java.util.TimerTask;
import java.util.UUID;
import javax.swing.JFrame;
import Tools.TorrentInfo;

public class RUBTClient extends JFrame implements Runnable{

	private static final long serialVersionUID = 1L;
	
	private static final Logger logger = Logger.getLogger(RUBTClient.class.getName());
    //peer ip
    String onlyPeer = null;
	
    //torrent downloadfile
	String downloadFileName;
    
    //torrent metafileinfo
    String metainfoFileName;
	
    //raw bytes downloaded
	volatile RandomAccessFile theDownloadFile;
	
    //peer id->client
	String clientId;
	
    //bytes left to download
	volatile int bytesLeft;
	
    //bytes uploaded to peers
	volatile int bytesUploaded = 0;

    //bytes downloaded so far
	volatile int bytesDownloaded = 0;
	
    //metainfo file of torrent
	TorrentInfo torrentInfo;
	
    //response from tracker
	Response response1;

    //another tracker response
	Response response2;

	 //arraylist stores peers
	volatile ArrayList<Peer> peers;
	
    //list of peers
	volatile ArrayList<Peer> neighboring_peers = new ArrayList<Peer>(10);
	
    //list of peers client shoudn't connect to
	volatile ArrayList<String> bad_peers = new ArrayList<String>(10);
	
    //number of active peers
	volatile int numOfActivePeers = 0;
	
    //number of peers client is trying to connect
	volatile int numOfAttemptedConnections = 0;
	
    //max number of connections
	volatile int MAX_CONNECTIONS = 10;
	
    //amount of time client try to connect to peer
	volatile int MAX_CONNECTION_ATTEMPTS = 3;
	
    //boolean array whether piece is downloaded
	volatile boolean[] havePieces;
	
    //number of block to download
	volatile int numOfPieces;
	
    //number of pieces client has
	volatile int numOfHavePieces = 0;
	
    //announcement interval
	static int announceTimerInterval = 180;
	
    //# of bytes that can uploaded max per peer
	volatile int UPLOAD_LIMIT;
	
	volatile int MAX_UPLOAD_LIMIT = 130000;
	
    //lower bound
	volatile int UPLOAD_LOWER_LIMIT = 33000;
	
    //download limit
	volatile int DOWNLOAD_LIMIT;
	
	volatile int MAX_DOWNLOAD_LIMIT = 390000;
	
	int DOWNLOAD_LOWER_LIMIT = 33000;
	
    //if client seeding
	volatile boolean amSeeding = false;
	
    //how far along download
	int percentComplete;

	final long CHOKING_INTERVAL = 30000;
	
    //# choked peers
	volatile int numOfUnchokedPeers;
	
	volatile int MAX_UNCHOKED_PEERS = 4;
	
	//peers being choked
	volatile ArrayList<Peer> choked_peers = new ArrayList<Peer>(10);
	
	//peers unchoked
	volatile ArrayList<Peer> unchoked_peers = new ArrayList<Peer>(10);

	volatile boolean RUN = true;
	
	public RUBTClient() {
		
		// Generate peer ID for the client
		String str = UUID.randomUUID().toString();
		this.clientId = "-RUBT11-" + str.substring(0,8) + str.substring(9,13);
    }
	
	public static void main(String[] args) {
		
		RUBTClient c = new RUBTClient();
		
		// Make sure that the number of command line arguments is 2 or 3
		if (args.length != 2 && args.length != 3) {
            System.out.println("Usage: java RUBTClient <torrent file> <download file>\n");
            System.out.println("or\n");
            System.out.println("Usage: java RUBTClient <torrent file> <download file> <peer ip>\n");
            return;
        }

		c.metainfoFileName = args[0];
		c.downloadFileName = args[1];
		if (args.length == 3) c.onlyPeer = args[2];
        //torrent metainfo file ->torrent info file
        c.torrentInfo = MyTools.getTorrentInfo(c.metainfoFileName);
        c.numOfPieces = c.torrentInfo.piece_hashes.length;
	    File file = new File(c.downloadFileName);
    
        if (file.exists()) {
        	logger.info("resuming download.");
        	try {
				c.theDownloadFile = new RandomAccessFile(file, "rwd");
				MyTools.setDownloadedBytes(c);
			} catch (IOException e) {
				e.printStackTrace();
			}
        } else {
        	//fresh download
        	logger.info("Start new download.");
	        c.bytesLeft = c.torrentInfo.file_length;
	        c.havePieces = new boolean[c.numOfPieces];
	        try {
	        	file.createNewFile();
				c.theDownloadFile = new RandomAccessFile(file, "rwd");
				c.theDownloadFile.setLength(c.torrentInfo.file_length);
			} catch (IOException e) {
				e.printStackTrace();
			}
        }
        c.percentComplete = c.numOfHavePieces*100/c.numOfPieces;
        if (c.numOfHavePieces == c.numOfPieces) c.amSeeding = true;
        //connect tracker -> send announcement
        new Request(c, "started");
        logger.info("Sent started announcement to tracker.");
        
        //check what wrong -> print failure reason
        if (c.response1.failure_reason != null) {
        	System.out.println("Torrent failed to download, failure reason:\n   " + c.response1.failure_reason);
        	logger.info(c.response1.failure_reason);
        	return;
        }
        
        // Input Reader
        InputReader ir = c.new InputReader();
        ir.start();
        
        //peermap -> peer array
        ArrayList<Map<ByteBuffer, Object>> peer_map = c.response1.get_peer_map();
        c.peers = MyTools.createPeerList(c, peer_map);
        // Start connecting to and messaging peers
        for (Peer peer : c.peers) {
			if (c.numOfAttemptedConnections < c.MAX_CONNECTIONS) {
                    if(c.onlyPeer != null && peer.peerIp.equals(c.onlyPeer) && !peer.peerId.equals(c.clientId)){
					System.out.println("PEER ID: " + peer.peerId + " CLIENT ID: " + c.clientId);
	        		System.out.println("Starting thread for Peer ID : [ " + peer.peerId + " ] and IP : [ " + peer.peerIp +" ].");
	    			peer.start();
				}
				else if(c.onlyPeer == null){
	        		System.out.println("Starting thread for Peer ID : [ " + peer.peerId + " ] and IP : [ " + peer.peerIp +" ].");
	    			peer.start();
				}
			}
			else{
    			logger.info("Unable to connect with Peer ID : [ " + peer.peerId + " ]. Maximum number of connections reached.");
			}
        }

        // Thread start for client
        new Thread(c).start();
        startGui(c);
        

		PeerListener peerListener = c.new PeerListener(c.onlyPeer);
		peerListener.runPeerListener();
	}
    //thread for client
	public void run() {
		
        logger.info("Started thread for client. Our peer ID: " + this.clientId);
		//set timer
		Timer t = new Timer();
		TimerTask tTask = new TimerTask() {
			@Override
			public void run() {
				// Send the regular announcement
				new Request(RUBTClient.this, "");
				logger.info("Regular announcement sent to tracker.");
				// Update the tracker announce interval
				if (RUBTClient.this.response2 != null)
					RUBTClient.announceTimerInterval = 
						(RUBTClient.this.response2.interval > 180) ? 180 : RUBTClient.this.response2.interval;
				
				if (RUBTClient.this.onlyPeer == null) {
					// parse tracker -> add in peers
					ArrayList<Map<ByteBuffer, Object>> peer_map = RUBTClient.this.response2.get_peer_map();
			        ArrayList<Peer> peers = MyTools.createPeerList(RUBTClient.this, peer_map);
			        for (Peer peer : peers) {
			        	if (RUBTClient.this.numOfAttemptedConnections < RUBTClient.this.MAX_CONNECTIONS 
			        			&& !RUBTClient.this.neighboring_peers.contains(peer) 
			        			&& !RUBTClient.this.bad_peers.contains(peer.peerIp)) {
			        		System.out.println("Added peer");
			        		
			        		RUBTClient.this.peers.add(peer);
			        		System.out.println("Starting thread for Peer ID : [ " + peer.peerId + " ] and IP : [ " + peer.peerIp +" ].");
			    			peer.start();
			        	}
			        }
		        
			        // Look at the old peers and see if any of them deserve a chance to be connected to, again
			        for (Peer peer : RUBTClient.this.peers) {
			        	if (peer.connectionAttempts < RUBTClient.this.MAX_CONNECTION_ATTEMPTS 
			        			&& (peer.peerIp != null ? !RUBTClient.this.neighboring_peers.contains(peer) : true) // Adjusted for incoming peer connections
			        			// In case I add something later on that puts peers in bad_peers even when connectionAttempts < MAX_CONNECTION_ATTEMPTS
			        			&& !RUBTClient.this.bad_peers.contains(peer.peerIp)) {
			        		System.out.println("Starting NEW thread for Peer ID : [ " + peer.peerId + " ] and IP : [ " + peer.peerIp +" ].");
			    			peer.start();
			        	}
			        }
				}
			}
		};
		if (!this.amSeeding) t.schedule(tTask, RUBTClient.announceTimerInterval*1000, RUBTClient.announceTimerInterval*1000);
        TimerTask tTask2 = new TimerTask() {
        	
        	public void run() {
        	//if one of lists empty-> exit
        		if (RUBTClient.this.choked_peers.isEmpty() || RUBTClient.this.unchoked_peers.isEmpty()) {
        			System.out.println("Can't optimistically choke/unchoke, one of the lists is empty");
        			return;
        		}
        		
        		//if seed ->connect peers with fast upload
        		if (RUBTClient.this.amSeeding) {
	        		Peer peer1 = null, peer2 = null, peer3 = null, peer4 = null, peer5 = null, peer6 = null, peer7 = null, peer8 = null;
	        		for (Peer peer : RUBTClient.this.choked_peers) {
	        			if (peer1 == null) 
	        				peer1 = peer;
	        			else {
	            			if (peer2 == null) {
	            				if (peer1.uploadRate > peer.uploadRate) {
	            					peer2 = peer;
	            				} else {
	            					peer2 = peer1;
	            					peer1 = peer;
	            				}
	            			} else {
	                			if (peer3 == null) { 
	                				if (peer1.uploadRate > peer.uploadRate) {
	                					if (peer2.uploadRate > peer.uploadRate) {
	                						peer3 = peer;
	                					} else {
	                						peer3 = peer2;
	                						peer2 = peer;
	                					}
	                				} else {
	                					peer3 = peer2;
	                					peer2 = peer1;
	                					peer1 = peer;
	                				}
	                				
	                			}
	                			else {
	                				if (peer4 == null) { 
	                    				if (peer1.uploadRate > peer.uploadRate) {
	                    					if (peer2.uploadRate > peer.uploadRate) {
	                    						if (peer3.uploadRate > peer.uploadRate) {
	                    							peer4 = peer;
	                    						} else {
	                    							peer4 = peer3;
	                    							peer3 = peer;
	                    						}
	                    					} else {
	                    						peer4 = peer3;
	                    						peer3 = peer2;
	                    						peer2 = peer;
	                    					}
	                    				} else {
	                    					peer4 = peer3;
	                    					peer3 = peer2;
	                    					peer2 = peer1;
	                    					peer1 = peer;
	                    				}
	                    			}
	                    			else {
	                    				if (peer1.uploadRate > peer.uploadRate) {
	                    					if (peer2.uploadRate > peer.uploadRate) {
	                    						if (peer3.uploadRate > peer.uploadRate) {
	                    							if (peer4.uploadRate < peer.uploadRate)
	                    								peer4 = peer;
	                    						} else {
	                    							peer4 = peer3;
	                    							peer3 = peer;
	                    						}
	                    					} else {
	                    						peer4 = peer3;
	                    						peer3 = peer2;
	                    						peer2 = peer;
	                    					}
	                    				} else {
	                						peer4 = peer3;
	                						peer3 = peer2;
	                						peer2 = peer1;
	                						peer1 = peer;
	                    				}
	                    			}
	                    			if (peer1.uploadRate > peer.uploadRate) {
	                    				if (peer2.uploadRate > peer.uploadRate) {
	                    					if (peer3.uploadRate < peer.uploadRate)
	                    						peer3 = peer;
	                    				} else {
	                    					peer3 = peer2;
	                    					peer2 = peer;
	                    				}
	                    			} else {
	                    				peer3 = peer2;
	                    				peer2 = peer1;
	                    				peer1 = peer;
	                    			}
	                			}
	                			if (peer1.uploadRate > peer.uploadRate) {
	                				if (peer2.uploadRate < peer.uploadRate)
	                					peer2 = peer;
	                			} else {
	                				peer2 = peer1;
	                				peer1 = peer;
	                			}
	            			}
	        				peer1 = (peer1.uploadRate > peer.uploadRate) ? peer1 : peer;
	        			}
	        		}
	        		
	        	//find peers with smallest upload rates
	        		for (Peer peer : RUBTClient.this.unchoked_peers) {
	        			if (peer.requestSendQueue.isEmpty()) {
	        				if (peer8 == null) 
	            				peer8 = peer;
	            			else {
	                			if (peer7 == null) {
	                				if (peer8.uploadRate < peer.uploadRate) {
	                					peer7 = peer;
	                				} else {
	                					peer7 = peer8;
	                					peer8 = peer;
	                				}
	                			} else {
	                    			if (peer6 == null) { 
	                    				if (peer8.uploadRate < peer.uploadRate) {
	                    					if (peer7.uploadRate < peer.uploadRate) {
	                    						peer6 = peer;
	                    					} else {
	                    						peer6 = peer7;
	                    						peer7 = peer;
	                    					}
	                    				} else {
	                    					peer6 = peer7;
	                    					peer7 = peer8;
	                    					peer8 = peer;
	                    				}
	                    				
	                    			}
	                    			else {
	                    				if (peer5 == null) { 
	                        				if (peer8.uploadRate < peer.uploadRate) {
	                        					if (peer7.uploadRate < peer.uploadRate) {
	                        						if (peer6.uploadRate < peer.uploadRate) {
	                        							peer5 = peer;
	                        						} else {
	                        							peer5 = peer6;
	                        							peer6 = peer;
	                        						}
	                        					} else {
	                        						peer5 = peer6;
	                        						peer6 = peer7;
	                        						peer7 = peer;
	                        					}
	                        				} else {
	                        					peer5 = peer6;
	                        					peer6 = peer7;
	                        					peer7 = peer8;
	                        					peer8 = peer;
	                        				}
	                        			}
	                        			else {
	                        				if (peer8.uploadRate < peer.uploadRate) {
	                        					if (peer7.uploadRate < peer.uploadRate) {
	                        						if (peer6.uploadRate < peer.uploadRate) {
	                        							if (peer5.uploadRate > peer.uploadRate)
	                        								peer5 = peer;
	                        						} else {
	                        							peer5 = peer6;
	                        							peer6 = peer;
	                        						}
	                        					} else {
	                        						peer5 = peer6;
	                        						peer6 = peer7;
	                        						peer7 = peer;
	                        					}
	                        				} else {
	                    						peer5 = peer6;
	                    						peer6 = peer7;
	                    						peer7 = peer8;
	                    						peer8 = peer;
	                        				}
	                        			}
	                        			if (peer8.uploadRate < peer.uploadRate) {
	                        				if (peer7.uploadRate < peer.uploadRate) {
	                        					if (peer6.uploadRate > peer.uploadRate)
	                        						peer6 = peer;
	                        				} else {
	                        					peer6 = peer7;
	                        					peer7 = peer;
	                        				}
	                        			} else {
	                        				peer6 = peer7;
	                        				peer7 = peer8;
	                        				peer8 = peer;
	                        			}
	                    			}
	                    			if (peer8.uploadRate < peer.uploadRate) {
	                    				if (peer7.uploadRate > peer.uploadRate)
	                    					peer7 = peer;
	                    			} else {
	                    				peer7 = peer8;
	                    				peer8 = peer;
	                    			}
	                			}
	            				peer8 = (peer8.uploadRate > peer.uploadRate) ? peer8 : peer;
	            			}
	        			}
	        		}
	        		
	        		//switch peers
	        		if (peer1 != null) {
	        			if (peer8 != null) if (!peer8.amInterested) {
	        				chokeUnchoke(RUBTClient.this, peer8, peer1);
							peer8 = null;
	        			} else if (peer7 != null) if (!peer7.amInterested) {
	        				chokeUnchoke(RUBTClient.this, peer7, peer1);
							peer7 = null;
	        			} else if (peer6 != null) if (!peer6.amInterested) {
	        				chokeUnchoke(RUBTClient.this, peer6, peer1);
							peer6 = null;
	        			} else if (peer5 != null) if (!peer5.amInterested) {
	        				chokeUnchoke(RUBTClient.this, peer5, peer1);
							peer5 = null;
	        			}
	        		}
	        		if (peer2 != null) {
	        			if (peer8 != null) if (!peer8.amInterested) {
	        				chokeUnchoke(RUBTClient.this, peer8, peer2);
							peer8 = null;
	        			} else if (peer7 != null) if (!peer7.amInterested) {
	        				chokeUnchoke(RUBTClient.this, peer7, peer2);
							peer7 = null;
	        			} else if (peer6 != null) if (!peer6.amInterested) {
	        				chokeUnchoke(RUBTClient.this, peer6, peer2);
							peer6 = null;
	        			} else if (peer5 != null) if (!peer5.amInterested) {
	        				chokeUnchoke(RUBTClient.this, peer5, peer2);
							peer5 = null;
	        			}
	        		}
	        		if (peer3 != null) {
	        			if (peer8 != null) if (!peer8.amInterested) {
	        				chokeUnchoke(RUBTClient.this, peer8, peer3);
							peer8 = null;
	        			} else if (peer7 != null) if (!peer7.amInterested) {
	        				chokeUnchoke(RUBTClient.this, peer7, peer3);
							peer7 = null;
	        			} else if (peer6 != null) if (!peer6.amInterested) {
	        				chokeUnchoke(RUBTClient.this, peer6, peer3);
							peer6 = null;
	        			} else if (peer5 != null) if (!peer5.amInterested) {
	        				chokeUnchoke(RUBTClient.this, peer5, peer3);
							peer5 = null;
	        			}
	        		}
	        		if (peer4 != null) {
	        			if (peer8 != null) if (!peer8.amInterested) {
	        				chokeUnchoke(RUBTClient.this, peer8, peer4);
							peer8 = null;
	        			} else if (peer7 != null) if (!peer7.amInterested) {
	        				chokeUnchoke(RUBTClient.this, peer7, peer4);
							peer7 = null;
	        			} else if (peer6 != null) if (!peer6.amInterested) {
	        				chokeUnchoke(RUBTClient.this, peer6, peer4);
							peer6 = null;
	        			} else if (peer5 != null) if (!peer5.amInterested) {
	        				chokeUnchoke(RUBTClient.this, peer5, peer4);
							peer5 = null;
	        			}
	        		}
	        	}
	        	//unchoke
	        	if (!RUBTClient.this.choked_peers.isEmpty()) {
	        		if (RUBTClient.this.numOfUnchokedPeers < RUBTClient.this.MAX_UNCHOKED_PEERS) {
	        			Peer peer = RUBTClient.this.choked_peers.get(0);
	        			RUBTClient.this.unchoked_peers.add(peer);
	        			peer.sendMessage(Message.createUnchoke());
	        			RUBTClient.this.numOfUnchokedPeers++;
	        		} else {
	        			for (Peer peer : RUBTClient.this.unchoked_peers) {
	        				if (!peer.amInterested) {
	        					chokeUnchoke(RUBTClient.this, peer, RUBTClient.this.choked_peers.get(0));
	        					return;
	        				}
	        			}
	        			chokeUnchoke(RUBTClient.this, RUBTClient.this.unchoked_peers.get(1), RUBTClient.this.choked_peers.get(0));
	        		}
	        	}
        	}
        };
        t.schedule(tTask2, this.CHOKING_INTERVAL, this.CHOKING_INTERVAL);
		
		int numOfActivePeers = this.numOfActivePeers;
		int percentComplete;
		if (this.numOfHavePieces != this.numOfPieces)
			percentComplete = this.numOfHavePieces*100/this.numOfPieces;
		else
			percentComplete = 100;
		System.out.println("Download is " + percentComplete + "% complete.");
		while(this.RUN) {
			
			// Set the amount completed
			this.percentComplete = this.numOfHavePieces*100/this.numOfPieces;
			
			// Set the upload and download limits for each peer
			if (numOfActivePeers != this.numOfActivePeers) {
				if (this.numOfActivePeers < 2) {
					this.UPLOAD_LIMIT = this.MAX_UPLOAD_LIMIT;
					this.DOWNLOAD_LIMIT = this.MAX_DOWNLOAD_LIMIT;
				} else {
					this.UPLOAD_LIMIT = 
							(this.MAX_UPLOAD_LIMIT / this.numOfActivePeers < this.torrentInfo.piece_length) ? this.UPLOAD_LOWER_LIMIT : (this.MAX_UPLOAD_LIMIT / this.numOfActivePeers);
					this.DOWNLOAD_LIMIT = 
							(this.MAX_DOWNLOAD_LIMIT / this.numOfActivePeers < this.torrentInfo.piece_length) ? this.DOWNLOAD_LOWER_LIMIT : (this.MAX_DOWNLOAD_LIMIT / this.numOfActivePeers);
				}
				numOfActivePeers = this.numOfActivePeers;
			}
			if (!this.amSeeding) {
				if (percentComplete != this.percentComplete) {
					System.out.println("Download is " + this.percentComplete + "% complete.");
					percentComplete = this.percentComplete;
					if (percentComplete == 100) {
						new Request(this, "completed");
						logger.info("Sent completed announcement to tracker.");
						logger.info("Seeding...");
					}
				}
				if (percentComplete == 100) {
					this.amSeeding = true;
					System.out.println("Now seeding, clearing request queue");
					for(Peer p : peers){
						p.requestSendQueue.clear();
					}
					t.cancel();
				}
			}
		}
		
	}
	public void chokeUnchoke(RUBTClient c, Peer peerChoke, Peer peerUnchoke) {
		
		c.unchoked_peers.remove(peerChoke);
		c.choked_peers.add(peerChoke);
		peerChoke.sendMessage(Message.createChoke());
		c.numOfUnchokedPeers--;
		
		c.choked_peers.remove(peerUnchoke);
		c.unchoked_peers.add(peerUnchoke);
		peerUnchoke.sendMessage(Message.createUnchoke());
		c.numOfUnchokedPeers++;
	}
//create gui
	private static void startGui(RUBTClient rc){
		Gui gui;
		gui = new Gui(rc);
		gui.setSize(460,450);
		gui.setResizable(false);
		gui.setVisible(true);
		gui.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
	}
	public int getProgressBarPercent() {
        return this.percentComplete;
	}
//listens for incoming connections
	class PeerListener extends Thread {
		
		String onlyPeer = null;
		
		public PeerListener() {
		}
		
		public PeerListener(String onlyPeer){
			this.onlyPeer = onlyPeer;
		}
		
		public void runPeerListener() {
			
			try (ServerSocket serverSocket = new ServerSocket(Request.port)){
				logger.info("PeerListener is waiting for connections.");
				
				while (true) {
					Socket peerSocket = serverSocket.accept();
					System.out.println("ACCEPTED A NEW PEER!!!!!!!!");
					Peer peer = new Peer(null, peerSocket.getInetAddress().getHostAddress(), Request.port, peerSocket, RUBTClient.this);
					RUBTClient.this.peers.add(peer);
					if (RUBTClient.this.numOfAttemptedConnections < RUBTClient.this.MAX_CONNECTIONS){
						new Thread(peer).start();
					}
				}
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
	}
//read input from user
	class InputReader extends Thread{
		
		public InputReader () {
			
		}
		
		public void run() {
			BufferedReader inputReader = new BufferedReader(new InputStreamReader(System.in));
			String str;
			
			try {
				str = inputReader.readLine().toLowerCase();
				while (true) {
					if (str.equals("q") || str.equals("quit")) break;
					if (str.equals("stop")) pause();
				}
			} catch (IOException e) {
				e.printStackTrace();
			}
			System.out.println("Quitting the client now");
			for (Peer peer : RUBTClient.this.neighboring_peers) {
				if (peer.socket != null) {
					peer.shutdown();
					RUBTClient.this.RUN = false;
					try {
						Thread.sleep(300);
					} catch (InterruptedException e) {
						e.printStackTrace();
					}
					System.exit(1);
				}
			}
		}
	}
	
	public void pause() {
		new Request(RUBTClient.this, "stopped");
	}

}
