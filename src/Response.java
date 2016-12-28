//Michael Bharrat 144001727
//Syed Zaidi 145000204
//Aaron Lukaszewics 088005488

import java.util.Map;
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import Tools.BencodingException;
import java.nio.ByteBuffer;
import Tools.Bencoder2;

public class Response {
	
	//Key used to retrieve the peer dictionary from the input byte array
	public final static ByteBuffer PEERS = ByteBuffer.wrap(new byte[]
		    {'p','e','e','r','s'});
	
	public final static ByteBuffer FAILURE_REASON = ByteBuffer.wrap(new byte[]
			{'f','a','i','l','u','r','e',' ','r','e','a','s','o','n'});
	
	public final static ByteBuffer TRACKER_ID = ByteBuffer.wrap(new byte[]
		    {'t','r','a','c','k','e','r',' ','i','d'});
	
	public final static ByteBuffer INTERVAL = ByteBuffer.wrap(new byte[]
			{'i','n','t','e','r','v','a','l'});
	
	public final static ByteBuffer COMPLETE = ByteBuffer.wrap(new byte[]
		    {'c','o','m','p','l','e','t','e'});
	
	public final static ByteBuffer INCOMPLETE = ByteBuffer.wrap(new byte[]
		    {'i','n','c','o','m','p','l','e','t','e'});
	
    //if tracker doesnt like connect. -> says why
	public String failure_reason;
    //tracker_id sent with announcm.
	public String tracker_id;
    //max time client waits
	public int interval;
    //# peers with file
	public int complete;
	//# leechers
	public int incomplete;
    //trackers initial resp
	public byte[] response_file_bytes;
	//The decoded dictionary of the tracker's response
	private Map<ByteBuffer, Object> response_file_map;
	
	private ArrayList<Map<ByteBuffer, Object>> peer_map;
	
	@SuppressWarnings("unchecked")
	public Response(byte[] rfb) throws BencodingException {
		
		//input is valid
		if(rfb == null || rfb.length == 0)
			throw new IllegalArgumentException("Torrent file bytes must be non-null and have at least 1 byte.");
        // Assign the byte array
		this.response_file_bytes = rfb;
		// Assign the metainfo map
		this.response_file_map = (Map<ByteBuffer,Object>)Bencoder2.decode(response_file_bytes);
		//check for failure
		ByteBuffer fBuf = (ByteBuffer) this.response_file_map.get(Response.FAILURE_REASON);
		if (fBuf != null) {
			try {
				this.failure_reason = new String(fBuf.array(), "ASCII");
			} catch (UnsupportedEncodingException e1) {
				e1.printStackTrace();
			}
			if (this.failure_reason != null || this.failure_reason != "") {
				return;
			}
		}

		//Extract the peer dictionary
		ArrayList<Map<ByteBuffer, Object>> peer_map = (ArrayList<Map<ByteBuffer, Object>>) this.response_file_map.get(Response.PEERS);
		if(peer_map == null)
			throw new BencodingException("Could not extract peer dictionary from tracker response dictionary.");
		this.set_peer_map(peer_map);
		
		//Set the tracker id
		String str;
		if ((str = (String)this.response_file_map.get(Response.TRACKER_ID)) != null)
			this.tracker_id = new String(str);
        
		//#seeders and #leechers
		int r;
		if ((r = (int) this.response_file_map.get(Response.COMPLETE)) != 0)
			this.complete = r;
		if ((r = (int) this.response_file_map.get(Response.INCOMPLETE)) != 0)
			this.incomplete = r;
		
		//Take note of the interval
		if ((r = (int) this.response_file_map.get(Response.INTERVAL)) != 0)
			this.interval = r;
	}
	//getter/setter methods
	public ArrayList<Map<ByteBuffer, Object>> get_peer_map() {
		return peer_map;
	}
	public void set_peer_map(ArrayList<Map<ByteBuffer, Object>> peer_map2) {
		this.peer_map = peer_map2;
	}
}
