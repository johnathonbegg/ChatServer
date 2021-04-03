
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JTextField;
public class Gui {
	Gui(){    
		JFrame f=new JFrame("Chat Service"); 
					//submit button
		JButton b=new JButton("Submit");    
		b.setBounds(100,100,140, 40);    
					//enter name label
		JLabel userLabel = new JLabel();		
		userLabel.setText("User input :");
		userLabel.setBounds(10, 10, 100, 100);//10,10,100,100
					//empty label which will show event after button clicked
		JLabel label1 = new JLabel();
		label1.setText("Server response :");
		label1.setBounds(10, 110, 200, 100);
		
		
					//textfield to enter name
		JTextField textfield= new JTextField();
		textfield.setBounds(110, 50, 200, 30);
		
		
					//add to frame
		f.add(label1);
		f.add(textfield);
		f.add(userLabel);
		f.add(b);    
		f.setSize(350,300);    
		f.setLayout(null);    
		f.setVisible(true);    
		f.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);   
		
							//action listener
		b.addActionListener(new ActionListener() {
	        
			@Override
			public void actionPerformed(ActionEvent arg0) {
					label1.setText("Server response here");				
			}          
	      });
		}         
	
	
		public static void main(String[] args) {    
		    new Gui();    
		}    
 }