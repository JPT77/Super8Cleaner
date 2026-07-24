package de.jpt.super8;
import javax.swing.SwingUtilities;
import org.opencv.core.Core;
public class Main{
 public static void main(String[] args){
  System.loadLibrary(Core.NATIVE_LIBRARY_NAME);
  SwingUtilities.invokeLater(() -> new VideoPlayer().setVisible(true));
 }
}
