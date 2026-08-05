package de.jpt.super8;
import javax.swing.SwingUtilities;


public class Main{

	public static void main(String[] args){
		SwingUtilities.invokeLater(() -> new VideoPlayer().setVisible(true));
	}

}
