//Michael Bharrat 144001727
//Syed Zaidi 145000204
//Aaron Lukaszewics 088005488

import java.util.Arrays;
import java.io.EOFException;
import java.io.IOException;
import java.security.NoSuchAlgorithmException;
import java.io.DataInputStream;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;
import java.security.MessageDigest;

public class Message {

	//class logger
	private static final Logger logger = Logger.getLogger(Message.class.getName());
	
	int lp;
	byte md = (byte) -1;
	int[] ipay = null;
	byte[] bpay = null;
	
	private static final String[] message_types = {
    "choke", "unchoke", "interested", "not_interested", "have", "bitfield", "request", "piece", "cancel"};
	//message construct
	
	public Message (byte rando) {
		this.lp = 1;
		this.md = rando;
	}
	
	// bitfield message construct.
	
	public Message (int lp, byte md, byte[] opay) {
		this.lp = lp;
		this.md = md;
		this.bpay = opay;
	}
	
	//message construct
	public Message (int lp, byte md, int pay) {
		this.lp = lp;
		this.md = md;
		int[] ipay = {pay};
		this.ipay = ipay;
	}
	
	//request/cancel mess. construct
	public Message (int lp, byte md, int[] pay) {
		this.lp = lp;
		this.md = md;
		this.ipay = pay;
	}
	
	//piece message construct
	public Message (int i, int start, byte[] rect) {
		this.lp = rect.length + 9;
		this.md = (byte) 7;
		int[] ipay = {i, start};
		this.ipay = ipay;
		this.bpay = rect;
	}
	
	//finds msg peer sends to client and deals
	public synchronized static void dealWithMessage(Peer p) throws IOException {
		
		//if nothing...return
		if (p.socketInput.available() == 0)
            return;
		
		DataInputStream str = p.socketInput;
		// Read the amount of bytes incoming
		int length = str.readInt();
		if (length > 131081 || length < 0 ){
			System.out.println("Invalid length " + length);
			return;
		}
		
        p.peerLastMessage = System.currentTimeMillis();
		
		//keep alive
		if (length == 0) {
            return;
		}
		
		//kill msg and prepare
        int pi, bo, bl, piece_length = p.client.torrentInfo.piece_length;
		byte[] block;
		try {
			// read 1st byte
			int b = str.readByte();
			
			if(b > 8 || b < 0 ){
				System.out.println("Unknown byte index: " + b);
				System.exit(1);
			}
            if(b != 4){
				System.out.println("Received {" + message_types[b] +"} message from IP : [ " + p.peerIp
						+ " ].");
			}
			
			switch (b) {
				case 0:
					p.peerChoking = true;
					break;
				case 1:
					p.peerChoking = false;
					// peer chokes...all requests dropped...resend
					while (!p.requestedQueue.isEmpty()) {
						p.requestSendQueue.put(p.requestedQueue.take());
					}
					break;
				case 2:
					p.peerInterested = true;
					if (p.amChoking && p.client.numOfUnchokedPeers < p.client.MAX_UNCHOKED_PEERS) {
						p.sendMessage(createUnchoke());
                        p.client.choked_peers.remove(p);
		        		p.client.numOfUnchokedPeers++;
		        		p.client.unchoked_peers.add(p);
					}
					break;
				case 3:
					p.peerInterested = false;
					if (!p.amInterested || p.client.amSeeding)
						p.shutdown();
					break;
				case 4:
					pi = str.readInt();
					//peerHaveArray to true
					if (!p.peerHaveArray[pi]) {
						p.peerHaveArray[pi] = true;
						p.peerHaveArraySize++;
						if (p.peerHaveArraySize != p.client.numOfPieces)
							p.percentPeerHas = p.peerHaveArraySize * 100 / p.client.numOfPieces;
						else
							p.percentPeerHas = 100;
					} else
						break;
                        //new request to get piece client missing
					if (!p.client.havePieces[pi]) {
						if (!p.sentGreetings) {
				        	p.sendMessage(createInterested());
				        	if (p.amChoking && p.client.numOfUnchokedPeers < p.client.MAX_UNCHOKED_PEERS) {
				        		p.sendMessage(createUnchoke());
				        		p.client.choked_peers.remove(p);
				        		p.client.numOfUnchokedPeers++;
				        		p.client.unchoked_peers.add(p);
				        	}
				        	p.sentGreetings = true;
						}
						p.wantFromPeer++;
						Message message = null;
						if (pi == p.client.numOfPieces - 1){
							message = createRequest(pi, 0, p.client.torrentInfo.file_length % piece_length);
						}
						else{
							message = createRequest(pi, 0, p.client.torrentInfo.piece_length);
						}
						p.requestSendQueue.add(message);
						for (Peer p1 : p.client.neighboring_peers) {
							if (p.peerId.equals(p1.peerId)) continue;
							if (p1.requestSendQueue.contains(message)) {
                                p1.requestSendQueue.put(message);
							}
						}
					}
					break;
				case 5:
                    p.badTorrentController = false;
					//store in peer
					byte[] peerBitfield = new byte[length - 1];
					str.readFully(peerBitfield);
					p.peerBitfield = peerBitfield;
					if (peerBitfield.length != (((double)p.client.numOfPieces)/8 == (double)(p.client.numOfPieces/8) ?
							p.client.numOfPieces/8 : p.client.numOfPieces/8 + 1)) {
						System.out.println("Incorrect bitfield size from Peer "
								+ "ID : [ " + p.peerId + "] with IP : [ " + p.peerIp
								+ " ].");
						p.shutdown();
						break;
					}
					for (int i = 0; i < p.client.numOfPieces; i++) {
						boolean isBitSet = MyTools.isBitSet(peerBitfield, i);
						if (isBitSet) {
							p.peerHaveArray[i] = true;
							p.peerHaveArraySize++;
							if (p.peerHaveArraySize != p.client.numOfPieces)
								p.percentPeerHas = p.peerHaveArraySize * 100 / p.client.numOfPieces;
							else
								p.percentPeerHas = 100;
						}
						if (isBitSet && !p.client.havePieces[i]) {
							if (!p.sentGreetings) {
							    p.sendMessage(createInterested());
					        	if (p.amChoking && p.client.numOfUnchokedPeers < p.client.MAX_UNCHOKED_PEERS) {
					        		p.sendMessage(createUnchoke());
					        		p.client.choked_peers.remove(p);
					        	}
							    p.sentGreetings = true;
							}
							p.wantFromPeer++;
							Message message = null;
							if (i == p.client.numOfPieces - 1){
								message = createRequest(i, 0, p.client.torrentInfo.file_length % piece_length);
							}
							else{
								message = createRequest(i, 0, p.client.torrentInfo.piece_length);
							}
							p.requestSendQueue.add(message);
							try{
								for (Peer p1 : p.client.neighboring_peers) {
									if (p.peerId.equals(p1.peerId)) continue;
	
									if (p1.requestSendQueue.contains(message)) {
										p1.requestSendQueue.remove(message);
										p1.requestSendQueue.put(message);
									}
								}
							}
							catch(Exception e){
								System.err.println("EXCEPTION ERROR: " + e);
							}
						}
					}
					break;
				case 6:
					//msg = request msg
                    pi = str.readInt();
					bo = str.readInt();
					bl = str.readInt();
					//if choke -> ignore
					if (p.amChoking) break;
					if (p.client.havePieces[pi]) {
						byte[] payload = new byte[bl];
						p.client.theDownloadFile.seek(pi * piece_length);
						p.client.theDownloadFile.read(payload, 0, payload.length);
						p.pieceSendQueue.offer(createPiece(pi, bo, payload));
					}
					break;
				case 7:
					pi = str.readInt();
					bo = str.readInt();
					block = new byte[length-9];
					str.readFully(block);
					
					if(p.client.havePieces[pi]){
						System.out.println("Client already has piece, don't need it");
						break;
					}
					//verify hash
					MessageDigest md = MessageDigest.getInstance("SHA");
					byte[] hash = null;
					hash = md.digest(block);
					boolean cray = true;
					byte[] checkWith = p.client.torrentInfo.piece_hashes[pi].array();
					for (int i = 0; i < checkWith.length; i++)
						if (checkWith[i] != hash[i]) cray = false;
					if (cray) {
						p.writing = true;
						p.client.theDownloadFile.seek(pi * piece_length);
						p.client.theDownloadFile.write(block, 0, block.length);
						p.client.bytesLeft -= block.length;
						p.client.bytesDownloaded += block.length;
						p.client.havePieces[pi] = true;
						p.client.numOfHavePieces++;
						p.downloadedFromPeer++;
						p.bytesFromPeer += block.length;
						p.requestsSent--;
						p.requestedQueue.remove(createRequest(pi, bo, block.length));
						p.bytes_received += block.length;
						p.fileBytesUploaded += block.length;
						for (Peer p1 : p.client.neighboring_peers)
							p1.haveSendQueue.offer(createHave(pi), 2500, TimeUnit.MILLISECONDS);
					} else {
						System.out.println("Requesting piece again");
						p.requestSendQueue.put(createRequest(pi, bo, length));
					}
					p.writing = false;
					break;
				case 8: // cancel msg
					pi = str.readInt();
					bo = str.readInt();
					bl = str.readInt();
					int[] c_payload = {pi, bo};
					for(Message m : p.pieceSendQueue){
						if(m.ipay == c_payload){
							System.out.println("Removed message from piece send queue after recv CANCEL");
							p.pieceSendQueue.remove(m);
							break;
						}	
					}
				default:
					System.out.println("Received unexpected message from peer, disconnecting peer");
					p.shutdown();
					break;
			}
		} catch(EOFException e) {
			logger.info("EOF exception from Peer : " + p.peerId);
			p.shutdown();
		} catch(IOException e) {
			logger.info("IO exception from Peer : " + p.peerId);
			e.printStackTrace();
		} catch (NoSuchAlgorithmException e) {
			logger.info("NoSuchAlgorithm exception from Peer : " + p.peerId);
			e.printStackTrace();
		} catch (InterruptedException e) {
			logger.info("Interrupted exception from Peer : " + p.peerId);
			e.printStackTrace();
		}
	}
	
	//verifies peer id in handshake matches
	public static boolean verifyHandshake(byte[] mtp, byte[] pr){
		if(pr.length != 68 || mtp.length!=68){
			System.err.println("response length wrong");
			return false;
		}
		byte[] sHash = Arrays.copyOfRange(mtp, 28, 47);
		byte[] rHash = Arrays.copyOfRange(pr, 28, 47);
		if (Arrays.equals(sHash, rHash)) {
			return true;
		}else{
			return false;
		}
	}
	//keep alive msg
	public static Message createKeepAlive() {
		return new Message((byte) -1);
	}
	
	//choke msg
	public static Message createChoke() {
		return new Message((byte) 0);
	}
    //unchoke msg
	public static Message createUnchoke() {
		return new Message((byte) 1);
	}
    //interested msg
	public static Message createInterested() {
		Message message = new Message((byte) 2);
		return message;
	}
	//uninterested msg
	public static Message createNotInterested() {
		return new Message((byte) 3);
	}
	//have msg
	public static Message createHave(int pi) {
		return new Message(5, (byte) 4, pi);
	}
	//bitfield msg
	public static Message createBitfield(RUBTClient rclie) {
		if (rclie.havePieces == null) return null;
		boolean cormore = false;
		byte[] bfield = 
				new byte[((double)rclie.numOfPieces)/8 == (double)(rclie.numOfPieces/8) ?
						rclie.numOfPieces/8 : rclie.numOfPieces/8 + 1];
		for (int i = 0; i < rclie.numOfPieces; i++) {
			if (rclie.havePieces[i]) {
				MyTools.setBit(bfield, i);
				cormore = true;
			}
		}
		if (cormore) 
			return new Message(bfield.length + 1, (byte) 5, bfield);
		else
			return null;
	}
	//request msg
	public static Message createRequest(int ind, int of, int l) {
		int[] payl = {ind, of, l};
		//return new Message(13, (byte) 6, payload);
		Message msg = new Message(13, (byte) 6, payl);
		return msg;
	}
	//piece msg
	public static Message createPiece(int pi, int bo, byte[] payl) {
		return new Message(pi, bo, payl);
	}
	
	
	public byte[] createCancel(RUBTClient rclie) {
		byte[] biteArray = null;
		// TODO
		return biteArray;
	}
}
