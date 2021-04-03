import java.io.Serializable;

public class Command implements Serializable {
	/**
	 * 
	 */
	private static final long serialVersionUID = 1L;
	private String message;

	public Command(String message) {
		this.message = message;

	}

	public String getMsg() {
		return message;

	}
}