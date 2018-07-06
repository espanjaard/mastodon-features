package org.mastodon.revised.mamut.feature;

import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Set;

import javax.swing.JCheckBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JSeparator;

import org.apache.commons.math3.stat.descriptive.DescriptiveStatistics;
import org.mastodon.graph.io.RawGraphIO.FileIdToGraphMap;
import org.mastodon.io.FileIdToObjectMap;
import org.mastodon.io.properties.DoublePropertyMapSerializer;
import org.mastodon.pool.PoolCollectionWrapper;
import org.mastodon.properties.DoublePropertyMap;
import org.mastodon.revised.bdv.SharedBigDataViewerData;
import org.mastodon.revised.model.feature.DoubleArrayFeature;
import org.mastodon.revised.model.feature.FeatureUtil;
import org.mastodon.revised.model.feature.FeatureUtil.Dimension;
import org.mastodon.revised.model.mamut.Model;
import org.mastodon.revised.model.mamut.Spot;
import org.mastodon.spatial.SpatialIndex;
import org.mastodon.spatial.SpatioTemporalIndex;
import org.scijava.plugin.Plugin;

import bdv.util.Affine3DHelpers;
import bdv.viewer.Source;
import bdv.viewer.SourceAndConverter;
import net.imglib2.Cursor;
import net.imglib2.FinalInterval;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.RealPoint;
import net.imglib2.realtransform.AffineTransform3D;
import net.imglib2.type.numeric.RealType;
import net.imglib2.util.Intervals;
import net.imglib2.view.IntervalView;
import net.imglib2.view.Views;

@Plugin( type = SpotFeatureComputer.class, name = "Spot median intensity" )
public class SpotMedianIntensityComputer extends SpotFeatureComputer
{

	public static final String KEY = "Spot median intensity";

	private static final String HELP_STRING =
			"Computes the median intensity inside spots "
					+ "over all sources of the dataset.";

	public SpotMedianIntensityComputer()
	{
		super( KEY );
	}

	private SharedBigDataViewerData bdvData;

	private boolean[] processSource;

	@Override
	public Set< String > getDependencies()
	{
		return Collections.emptySet();
	}

	@Override
	public void setSharedBigDataViewerData( final SharedBigDataViewerData bdvData )
	{
		this.bdvData = bdvData;
		this.processSource = new boolean[ bdvData.getSources().size() ];
		Arrays.fill( processSource, true );
	}

	@Override
	public Collection< String > getProjectionKeys()
	{
		if ( null == bdvData )
			return Collections.emptyList();

		final List< String > projectionKeys = new ArrayList<>( processSource.length * 2 );
		for ( int iSource = 0; iSource < processSource.length; iSource++ )
		{
			final String nameMedian = "Median ch " + iSource;
			projectionKeys.add( nameMedian );
		}
		return projectionKeys;
	}

	@Override
	public DoubleArrayFeature< Spot > compute( final Model model )
	{
		if ( null == bdvData )
			return null;

		// Calculation are made on resolution level 0.
		final int level = 0;
		// Affine transform holder.
		final AffineTransform3D transform = new AffineTransform3D();
		// Physical calibration holder.
		final double[] scales = new double[ 3 ];
		// Spot center position holder in image coords.
		final double[] pos = new double[ 3 ];
		// Spot center holder in image coords.
		final RealPoint center = RealPoint.wrap( pos );
		// Spot center position holder in integer image coords.
		final long[] p = new long[ 3 ];

		// Holder for property map.
		final ArrayList< SourceAndConverter< ? > > sources = bdvData.getSources();
		int nSourcesToProcess = 0;
		for ( final boolean pSource : processSource )
			if ( pSource )
				nSourcesToProcess++;
		final List< DoublePropertyMap< Spot > > pms = new ArrayList<>( nSourcesToProcess );
		final List< String > names = new ArrayList<>( nSourcesToProcess );
		final List< String > units = new ArrayList<>( nSourcesToProcess );

		final SpatioTemporalIndex< Spot > index = model.getSpatioTemporalIndex();
		final int numTimepoints = bdvData.getNumTimepoints();
		final int nSources = sources.size();

		// Initiate the statistics array for the median calculation and clear
		// for
		// each loop.
		final DescriptiveStatistics intensities = new DescriptiveStatistics();
		final EllipsoidInsideTest insideTest = new EllipsoidInsideTest();
		// Stores the cursor location but in global coordinates.
		final double[] globalPos = new double[ 3 ];
		final RealPoint globalRP = RealPoint.wrap( globalPos );

		for ( int iSource = 0; iSource < nSources; iSource++ )
		{
			if ( !processSource[ iSource ] )
				continue;

			final PoolCollectionWrapper< Spot > vertices = model.getGraph().vertices();
			final DoublePropertyMap< Spot > pmMedian = new DoublePropertyMap<>( vertices, Double.NaN );
			pms.add( pmMedian );
			final String nameMedian = "Median ch " + iSource;
			names.add( nameMedian );
			units.add( FeatureUtil.dimensionToUnits( Dimension.INTENSITY, spaceUnits, timeUnits ) );

			final Source< ? > source = sources.get( iSource ).getSpimSource();
			for ( int timepoint = 0; timepoint < numTimepoints; timepoint++ )
			{
				final SpatialIndex< Spot > spatialIndex = index.getSpatialIndex( timepoint );
				source.getSourceTransform( timepoint, level, transform );
				for ( int d = 0; d < scales.length; d++ )
					scales[ d ] = Affine3DHelpers.extractScale( transform, d );

				@SuppressWarnings( "unchecked" )
				final RandomAccessibleInterval< RealType< ? > > rai = ( RandomAccessibleInterval< RealType< ? > > ) source.getSource( timepoint, level );

				for ( final Spot spot : spatialIndex )
				{
					// Spot location in pixel units.
					transform.applyInverse( center, spot );

					// Compute (pessimistic) bounding box.
					final double rmax = Math.sqrt( spot.getBoundingSphereRadiusSquared() );
					final long[] min = new long[ rai.numDimensions() ];
					final long[] max = new long[ rai.numDimensions() ];
					for ( int d = 0; d < pos.length; d++ )
					{
						p[ d ] = Math.round( pos[ d ] );
						final int r = ( int ) Math.ceil( rmax / scales[ d ] );
						min[ d ] = p[ d ] - r;
						max[ d ] = p[ d ] + r;
					}
					final FinalInterval interval = new FinalInterval( min, max );
					final FinalInterval bb = Intervals.intersect( interval, rai );

					// Iterate through the bounding-box, and skip when we are
					// not in the ellipsoid.
					intensities.clear();
					final IntervalView< RealType< ? > > view = Views.interval( rai, bb );
					final Cursor< RealType< ? > > cursor = view.localizingCursor();
					while ( cursor.hasNext() )
					{
						cursor.fwd();
						// Transform coordinates from local to global.
						transform.apply( cursor, globalRP );

						// Test if we are inside the ellipsoid.
						if ( !insideTest.isPointInside( globalPos, spot ) )
							continue;

						intensities.addValue( cursor.get().getRealDouble() );
					}

					// Get the median (or the percentile of 50):
					final double median = intensities.getPercentile( 50 );
					pmMedian.set( spot, median );
				}
			}
		}
		return new DoubleArrayFeature<>( KEY, Spot.class, pms, names, units );
	}

	@Override
	public DoubleArrayFeature< Spot > deserialize( final File file, final Model support, final FileIdToGraphMap< ?, ? > fileIdToGraphMap ) throws IOException
	{
		try (final ObjectInputStream ois = new ObjectInputStream(
				new BufferedInputStream(
						new FileInputStream( file ), 1024 * 1024 ) ))
		{
			// NUMBER OF ELEMENTS
			final int nSources = ois.readInt();
			final List< DoublePropertyMap< Spot > > propertyMaps = new ArrayList<>();
			final List< String > names = new ArrayList<>();
			final List< String > units = new ArrayList<>();
			for ( int i = 0; i < nSources; i++ )
			{
				// NAME OF ENTRIES
				final String name = ois.readUTF();
				names.add( name );
				// UNITS.
				final String unit = ois.readUTF();
				units.add( unit );
				// NUMBER OF ENTRIES and ENTRIES
				final PoolCollectionWrapper< Spot > vertices = support.getGraph().vertices();
				final DoublePropertyMap< Spot > pm = new DoublePropertyMap<>( vertices, Double.NaN, vertices.size() );
				@SuppressWarnings( "unchecked" )
				final FileIdToObjectMap< Spot > idToSpotMap = ( FileIdToObjectMap< Spot > ) fileIdToGraphMap.vertices();
				final DoublePropertyMapSerializer< Spot > serializer = new DoublePropertyMapSerializer<>( pm );
				serializer.readPropertyMap( idToSpotMap, ois );
				propertyMaps.add( pm );
			}
			return new DoubleArrayFeature<>( KEY, Spot.class, propertyMaps, names, units );
		}
		catch ( final ClassNotFoundException e )
		{
			e.printStackTrace();
		}
		return null;
	}

	@Override
	public String getHelpString()
	{
		return HELP_STRING;
	}

	@Override
	public JComponent getConfigPanel()
	{
		if ( null == bdvData )
			return null;

		final int nSources = bdvData.getSources().size();
		final JPanel configPanel = new JPanel();
		configPanel.setLayout( new GridBagLayout() );
		final GridBagConstraints c = new GridBagConstraints();
		c.insets = new Insets( 0, 5, 0, 5 );
		c.anchor = GridBagConstraints.LINE_START;
		c.gridy = 0;
		c.weightx = 1.;
		c.fill = GridBagConstraints.BOTH;

		final JLabel lbl = new JLabel( "Data to analyze:" );
		configPanel.add( lbl, c );
		c.gridy++;

		configPanel.add( new JSeparator(), c );
		c.gridy++;

		for ( int i = 0; i < nSources; i++ )
		{
			final int currentSource = i;
			final String str = "ch " + currentSource + ": " + bdvData.getSources().get( currentSource ).getSpimSource().getName();
			final JCheckBox processBox = new JCheckBox( str, processSource[ currentSource ] );
			processBox.addItemListener( ( e ) -> processSource[ currentSource ] = processBox.isSelected() );
			configPanel.add( processBox, c );
			c.gridy++;
		}

		return configPanel;

	}
}
