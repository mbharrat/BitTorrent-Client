//Michael Bharrat 144001727
//Syed Zaidi 145000204
//Aaron Lukaszewics 088005488

import java.net.MalformedURLException;
import java.io.ByteArrayOutputStream;
import Tools.BencodingException;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.Socket;
import java.io.DataInputStream;


public class Request {
	static final int port = 6881;
	boolean ePD = false;
	
	String iHash;
	
	String cId;
	
	public Request(RUBTClient c, String ev) {
		//def. piece of url, assemble url
		String announceURL = c.torrentInfo.announce_url.toString();
		this.iHash = MyTools.toHex((c.torrentInfo.info_hash).array(), true);
		this.cId = MyTools.toHex(c.clientId, true);
		URL url = null;
		try {
			url = new URL(announceURL + "?info_hash=" + this.iHash + "&peer_id=" + cId + 
					"&port=" + port + "&uploaded=" + c.bytesUploaded + "&downloaded=" + c.bytesDownloaded +
					"&left=" + c.bytesLeft + "&event=" + ev);
		} catch (MalformedURLException e1) {
			System.out.println("url not formed correctly.");
			System.out.println("malformed url is: " + url);
			e1.printStackTrace();
		}
		//setup connection and connect
		HttpURLConnection urlC = null;
		DataInputStream iStr;
		byte[] rBy = null;
		try (Socket sock = new Socket(announceURL.substring(7, 20), Integer.parseInt(announceURL.substring(21, 25)))) {
			urlC = (HttpURLConnection) url.openConnection();
			if (urlC.getResponseCode() >= 400) {
				iStr = new DataInputStream(urlC.getErrorStream());
				System.out.println(iStr.readUTF());
				return;
			} else {
				iStr = new DataInputStream(urlC.getInputStream());
			}
			ByteArrayOutputStream bAOS = new ByteArrayOutputStream();
			byte[] rByte = new byte[1];
			while((iStr.read(rByte)) != -1) {
				bAOS.write(rByte);
			}
			rBy = bAOS.toByteArray();
			//decode tracker respn -> store in client
			try {
				if (ev.equals("started"))
					c.response1 = new Response(rBy);
				else
					c.response2 = new Response(rBy);
			} catch (BencodingException e) {
				e.printStackTrace();
			}
			bAOS.close();
			iStr.close();
			urlC.disconnect();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
}
