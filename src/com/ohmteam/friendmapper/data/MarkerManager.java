package com.ohmteam.friendmapper.data;

import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import android.app.Activity;
import android.graphics.Bitmap;
import android.os.Bundle;

import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.Projection;
import com.google.android.gms.maps.model.CameraPosition;
import com.google.android.gms.maps.model.LatLng;
import com.ohmteam.friendmapper.MapMarkerBitmapCallback;
import com.ohmteam.friendmapper.data.clustering.Clusterizer;
import com.ohmteam.friendmapper.io.ImageLoaderTask;
import com.ohmteam.friendmapper.util.ResultCallback;

/**
 * An object that manages a friends list and displays it as a series of markers. Friends in
 * overlapping locations will be joined as a group, and locations that get too close to each other
 * will be joined as a cluster. See {@link MapLocation} and {@link MapMarker} for details of the
 * cluster/group hierarchy.
 * 
 * An instance of this class can be pointed at a GoogleMap, where it will listen for camera change
 * events; it recalculates the groups and updates the marker display as the camera zoom changes.
 * 
 * @author Dylan
 */
public class MarkerManager {
	private final Activity activity;
	private GoogleMap map;
	private final int clusterRadius;

	private final List<FacebookFriend> friends = new LinkedList<FacebookFriend>();
	private final List<MapMarker> markers = new LinkedList<MapMarker>();

	/**
	 * Constructor.
	 * 
	 * @param activity A reference to an activity so that this manager can request to run tasks on
	 *        the UI thread (necessary for updating markers). TODO: maybe use a Handler instead, for
	 *        better encapsulation so that this class isn't coupled to Activity anymore.
	 * @param clusterRadius The number of pixels to use as the location clustering threshold. Note:
	 *        the clustering algorithm is very primitive at this time, so the clusterRadius is
	 *        simply used as a tile size for grid-based spacial bucketing.
	 */
	public MarkerManager(Activity activity, int clusterRadius) {
		this.activity = activity;
		this.clusterRadius = clusterRadius;
	}

	/**
	 * Point this manager at a GoogleMap, so that it can listen for changes in the zoom level and
	 * update the friend markers accordingly.
	 * 
	 * @param map The map for this manager to use.
	 */
	public void setMap(GoogleMap map) {
		if (map != null && map != this.map) {
			this.map = map;
			this.map.setOnCameraChangeListener(new CameraListener());
			activity.runOnUiThread(new RecalculateMarkersTask());
		}
	}

	/**
	 * Set the friends list, triggering a recalculation of the markers, and a UI update.
	 * 
	 * @param friends A list containing the friends to be displayed.
	 */
	public synchronized void setFriends(List<FacebookFriend> friends) {
		this.friends.clear();
		this.friends.addAll(friends);

		activity.runOnUiThread(new RecalculateMarkersTask());
	}

	/**
	 * Save the state (current friends list) of this manager to a Bundle.
	 * 
	 * @param bundle The bundle to save to.
	 */
	public void saveTo(Bundle bundle) {
		Bundle friendsList = FacebookFriendBundler.friendsToBundle(friends);
		bundle.putBundle("markerManagerFriends", friendsList);
	}

	/**
	 * Load a state (friends list) from the given bundle into this manager.
	 * 
	 * @param bundle The bundle to load from
	 */
	public void loadFrom(Bundle bundle) {
		Bundle friendsBundle = bundle.getBundle("markerManagerFriends");
		setFriends(FacebookFriendBundler.friendsFromBundle(friendsBundle));
	}

	/**
	 * Listens for changes in the map's camera zoom level. When the zoom changes, the markers should
	 * be recalculated.
	 */
	private class CameraListener implements GoogleMap.OnCameraChangeListener {
		private float latestZoom = 0;

		@Override
		public void onCameraChange(CameraPosition cam) {
			// only do work if the zoom is different from last time
			if (cam.zoom != latestZoom) {
				latestZoom = cam.zoom;
				Projection mapProjection = map.getProjection();
				recalculateMarkers(mapProjection);
			}
		}
	}

	/**
	 * A Runnable that calls `recalculateMarkers`. This exists so that code running outside of the
	 * UI thread can have the markers be updated within the UI thread.
	 */
	private class RecalculateMarkersTask implements Runnable {
		@Override
		public void run() {
			if (map != null) {
				recalculateMarkers(map.getProjection());
			}
		}
	}

	/**
	 * Based on the current friends list, calculates a hierarchy of clusters and locations, then
	 * adds the appropriate markers to the map.
	 * 
	 * @param mapProjection the GoogleMap's projection, used to transform back and forth between
	 *        Latitude/Longitude space and Pixel space.
	 */
	private void recalculateMarkers(Projection mapProjection) {

		// detach all of the previous markers, and clear the list
		for (MapMarker marker : markers) {
			marker.detach();
		}
		markers.clear();

		// -----------------------------------------------------------
		// assemble a list of MapLocations based on all of the friends
		// -----------------------------------------------------------

		// Group up friends by their common locations as a list of MapLocation
		List<MapLocation> locations = MapLocation.groupFriends(friends);

		// Initialize the clustering algorithm for a given cluster radius
		Clusterizer<MapLocation> clusterizer = new Clusterizer<MapLocation>(clusterRadius, mapProjection);

		// Run the clustering algorithm to get lists of MapLocations grouped by cluster center
		Map<LatLng, List<MapLocation>> clusters = clusterizer.findClusters(locations);

		// Use the clusters to add new markers to the map
		for (Entry<LatLng, List<MapLocation>> entry : clusters.entrySet()) {
			MapMarker marker = new MapMarker(entry.getKey(), entry.getValue());

			// Check to see if the marker is a singlet.
			if (marker.getNumFriends() == 1) {
				// If it is a singlet, check to see if the picture is loaded and saved as a bitmap
				// resource
				boolean imageLoaded = marker.getFirstFriend().imageLoaded();
				if (imageLoaded) {
					// If it is loaded, show the bitmap as is

				} else {
					// If it is not loaded, load an asynchronous loader task
					ExecutorService backgroundTaskService = Executors.newSingleThreadExecutor();
					ResultCallback<Bitmap> addMarkerCallback = new MapMarkerBitmapCallback(activity, map, marker);
					String imageUrl = marker.getFirstFriend().getProfilePicURL();// "https://fbcdn-profile-a.akamaihd.net/hprofile-ak-xfp1/t1.0-1/p200x200/1499616_10154032046470157_806633043_n.jpg";//
					Runnable loadImageTask = new ImageLoaderTask(imageUrl, addMarkerCallback);
					backgroundTaskService.execute(loadImageTask);
				}

			}
			marker.detach();
			marker.attach(map);
			markers.add(marker);
		}
	}

	public void removeAllMarkers() {

		// Clear the list of loaded Facebook friends so that the map is not
		// re-populated when re-sized

		List<FacebookFriend> emptyList = new LinkedList<FacebookFriend>();
		setFriends(emptyList);
		/*
		 * this.friends.clear();
		 * 
		 * // Remove each Marker from the map for (MapMarker marker : markers) { marker.detach(); }
		 */
	}

	public boolean friendsListIsEmpty() {
		return friends.isEmpty();
	}
}
