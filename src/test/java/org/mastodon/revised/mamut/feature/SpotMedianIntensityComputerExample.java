package org.mastodon.revised.mamut.feature;

import java.io.IOException;
import java.util.Iterator;
import java.util.Locale;

import javax.swing.UIManager;
import javax.swing.UnsupportedLookAndFeelException;

import org.mastodon.revised.mamut.MamutProject;
import org.mastodon.revised.mamut.MamutProjectIO;
import org.mastodon.revised.mamut.WindowManager;
import org.mastodon.revised.model.feature.DoubleArrayFeature;
import org.mastodon.revised.model.mamut.Model;
import org.mastodon.revised.model.mamut.Spot;
import org.scijava.Context;

import mpicbg.spim.data.SpimDataException;

public class SpotMedianIntensityComputerExample
{

	public static void main( final String[] args ) throws ClassNotFoundException, InstantiationException, IllegalAccessException, UnsupportedLookAndFeelException, IOException, SpimDataException
	{
		Locale.setDefault( Locale.ROOT );
		UIManager.setLookAndFeel( UIManager.getSystemLookAndFeelClassName() );

		final MamutProject project = new MamutProjectIO().load( "../TrackMate3/samples/mamutproject" );

		final Context context = new Context();
		final WindowManager windowManager = new WindowManager( context );
		windowManager.getProjectManager().open( project );
		final Model model = windowManager.getAppModel().getModel();

		final SpotMedianIntensityComputer computer = new SpotMedianIntensityComputer();
		computer.setSharedBigDataViewerData( windowManager.getAppModel().getSharedBdvData() );

		System.out.println( "\nComputing feature...." );
		final DoubleArrayFeature< Spot > feature = computer.compute( model );
		System.out.println( "Done." );

		final Iterator< Spot > iterator = model.getGraph().vertices().iterator();
		while ( iterator.hasNext() )
		{
			final Spot spot = iterator.next();
			final double[] values = feature.get( spot );
			System.out.println( String.format( "Spot %10s, median = %-10.1f", spot.getLabel(), values[ 0 ] ) );
			break;
		}
	}

}
