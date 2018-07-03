package org.mastodon.revised.mamut.feature;

import java.io.IOException;
import java.util.Locale;

import javax.swing.UIManager;
import javax.swing.UnsupportedLookAndFeelException;

import org.mastodon.revised.mamut.MainWindow;
import org.mastodon.revised.mamut.MamutProject;
import org.mastodon.revised.mamut.MamutProjectIO;
import org.mastodon.revised.mamut.WindowManager;
import org.scijava.Context;

import mpicbg.spim.data.SpimDataException;

public class TestDrive {

	public static void main(final String[] args) throws ClassNotFoundException, InstantiationException, IllegalAccessException, UnsupportedLookAndFeelException, IOException, SpimDataException {
		Locale.setDefault( Locale.ROOT );
		UIManager.setLookAndFeel( UIManager.getSystemLookAndFeelClassName() );

		final MamutProject project = new MamutProjectIO().load( "../TrackMate3/samples/mamutproject" );

		final Context context = new Context();
		final WindowManager windowManager = new WindowManager(context );
		windowManager.getProjectManager().open( project );
		
		final MainWindow mainWindow = new MainWindow(windowManager);
		mainWindow.setVisible(true);
	}
}
