import java.io.Serializable;

public class Responce implements Serializable{

	/**
	 * 
	 */
	private static final long serialVersionUID = 1L;
	private String[] reply;
	private boolean success; 
	
	public Responce(String[] reply){
		this.reply = reply;
	}

	public Responce(String string) {
		reply = new String[1];
		reply[0] = string;
	}

	public void printReply(){
		for (String str : reply){
			System.out.println(str);
		}
	}
	
	public String[] getReply(){
		return reply;
	}
	public void setSuccess(boolean success){
		this.success = success;
	}
	
	public boolean getSuccess(){
		return success;
	}
}