//Michael Bharrat 144001727
//Syed Zaidi 145000204
//Aaron Lukaszewics 088005488

import java.net.UnknownHostException;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.net.Socket;
import java.util.Map;
import java.nio.ByteBuffer;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import Tools.TorrentInfo;
import java.util.logging.Logger;
import java.util.ArrayList;
import Tools.BencodingException;


public class MyTools {
	private static final Logger logger = Logger.getLogger(MyTools.class.getName());
	//logger
	
    //metainfo file -> torrentinfo file
	public static TorrentInfo getTorrentInfo(String tfile) {
		FileInputStream fileInputStream;
        File metafile = new File(tfile);
        byte[] tbf = new byte[(int)metafile.length()];
        try {
        	fileInputStream = new FileInputStream(metafile);
        	fileInputStream.read(tbf);
        	fileInputStream.close();
        } catch (Exception e) {
        	System.out.println("metafile to byte array failure");
        	e.printStackTrace();
        }
        TorrentInfo ti = null;
		try {
			ti = new TorrentInfo(tbf);
		} catch (BencodingException e) {
			System.out.println("byte array of the metafile into a TorrentInfo file failure");
			e.printStackTrace();
		}
		return ti;
	}
	//byte arr/str ->hex str with percents
	public static String toHex(Object o, boolean boolio) {
		byte[] bA = null;
		if (o instanceof String) {
			String str = (String) o;
			bA = str.getBytes();
		} else if (o instanceof byte[]) {
			bA = (byte[]) o;
		}
		if (bA == null) return null;
		if (bA.length == 0) return "";
		StringBuilder strB= new StringBuilder();
		if (boolio)
			for (byte b : bA) {
				strB.append("%" + String.format("%02x", b));
			}
		else
			for (byte b : bA) {
				strB.append(String.format("%02x", b));
			}
		return strB.toString();
	}
	//peers in arraylist
	public static ArrayList<Peer> createPeerList(RUBTClient c, ArrayList<Map<ByteBuffer, Object>> peer_map) {
		ArrayList<Peer> persons = new ArrayList<Peer>();
        Peer p = null;
        ByteBuffer bBuf;
        String p_id;
        String p_ip;
        int p_port;
        for (int i = 0; i < peer_map.size(); i++) {
        	bBuf = (ByteBuffer)peer_map.get(i).get(ByteBuffer.wrap(new byte[] {'p', 'e', 'e', 'r', ' ', 'i', 'd'}));
            p_id = new String(bBuf.array());
           
            bBuf = (ByteBuffer)peer_map.get(i).get(ByteBuffer.wrap(new byte[] {'i', 'p'}));
            p_ip = new String(bBuf.array());
       
            p_port =  (Integer) peer_map.get(i).get(ByteBuffer.wrap(new byte[] { 'p', 'o', 'r', 't' }));
            p = new Peer(p_id, p_ip, p_port, null, c);
            persons.add(p);
        }
		return persons;
	}
	//find open port
	public static int findPort(String ippp) {
		for (int z = 6881; z < 6890; z++) {
			try (Socket sock = new Socket(ippp, z)) {
				if (sock != null) {
					return z;
				}
			} catch (UnknownHostException e) {
				e.printStackTrace();
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
		return 0;
	}
	//if shutdown+restarted, refills rawFileBytes
	public static void setDownloadedBytes(RUBTClient c) {
        c.bytesLeft = c.torrentInfo.file_length;
        c.havePieces = new boolean[c.numOfPieces];
		MessageDigest md = null;
		try {
			md = MessageDigest.getInstance("SHA");
		} catch (NoSuchAlgorithmException e) {
			e.printStackTrace();
		}
		int pl = c.torrentInfo.piece_length;
		byte[] piece = new byte[pl], getHash = new byte[20];
        for (int i = 0; i < c.numOfPieces; i++) {
        	piece = new byte[(i == c.numOfPieces - 1) ? c.torrentInfo.file_length % pl : piece.length];
        	try {
        		c.theDownloadFile.seek(i*pl);
				c.theDownloadFile.read(piece, 0, piece.length);
			} catch (IOException e) {
				e.printStackTrace();
			}
        	getHash = md.digest(piece);
        	boolean iG = true;
        	byte[] pHash = c.torrentInfo.piece_hashes[i].array();
        	for (int j = 0; j < pHash.length; j++)
        		if (pHash[j] != getHash[j]) iG = false;
        	if (iG) {
        		c.havePieces[i] = true;
        		c.bytesLeft -= piece.length;
        		c.bytesDownloaded += piece.length;
        		c.numOfHavePieces++;
        	}
        	md.reset();
        }
        logger.info("File found and loaded");
        return;
	}
    
	public static byte[] getFileBytes(RandomAccessFile f, int tfl) {
		if (f == null) return null; //sanity check
		byte[] bA = new byte[tfl];
		try {
			f.readFully(bA);
		} catch (IOException e) {
			System.err.println("There was an error converting the file into a byte array.");
			e.printStackTrace();
		}
		return bA;
	}
	//sets bit in byte array
	public static byte[] setBit(byte[] bA, int g) {
		bA[g/8] |= (1 << 7-g%8);
		return bA;
	}
	//resets bit
	public static byte[] resetBit(byte[] bA, int g) {
		bA[g/8] &= (1 << 7-g%8);
		return bA;
	}
	//if bit in byte set
	public static boolean isBitSet(byte[] bA, int g) {
		int q = (bA[g/8] >> 7-g%8) & 1;
		if (q == 0) return false;
		return true;
	}
}
