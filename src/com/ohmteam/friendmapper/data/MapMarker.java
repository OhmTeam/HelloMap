package com.ohmteam.friendmapper.data;

import java.util.List;

import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.model.BitmapDescriptor;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;

/**
 * Represents a collection of friends and locations that will be represented on a GoogleMap as a
 * single marker. This is the first level of the cluster/group hierarchy; a MapMarker represents a
 * cluster. A MapMarker may encompass several different locations (but can also represent exactly
 * one location and one friend), each of which might hold multiple friends.
 * 
 * @author Dylan
 */
public class MapMarker {
	private List<MapLocation> locations;
	private Marker marker = null;
	private final LatLng markerPos;

	/**
	 * Constructor.
	 * 
	 * @param markerPos The location on the map where the marker will be displayed
	 * @param locations A list of MapLocations that this marker represents
	 */
	public MapMarker(LatLng markerPos, List<MapLocation> locations) {
		this.markerPos = markerPos;
		this.locations = locations;
	}

	/**
	 * @return The number of friends contained in this marker, i.e. the sum of all friends in all
	 *         locations within this cluster.
	 */
	public int getNumFriends() {
		int count = 0;
		for (MapLocation loc : locations) {
			count += loc.friends.size();
		}
		return count;
	}

	/**
	 * @return The first friend in the first location in this marker's cluster. If for some reason
	 *         there are no friends, return null.
	 */
	public FacebookFriend getFirstFriend() {
		for (MapLocation loc : locations) {
			if (loc.friends.size() > 0) {
				return loc.friends.get(0);
			}
		}
		return null;
	}

	/**
	 * Creates a MarkerOptions instance which can be used to create the actual Marker to be added to
	 * a map.
	 * 
	 * @return The MarkerOptions describing the marker to be created
	 */
	public MarkerOptions getMarkerOptions() {
		MarkerOptions mo = new MarkerOptions();

		// set the marker's Position
		mo.position(markerPos);

		/*
		 * Set the marker's Title. If there is exactly one friend, use that friend's name as the
		 * title For multiple friends, display the number of friends.
		 */
		int numFriends = getNumFriends();
		String title = "?";
		if (numFriends > 1) {
			title = numFriends + " friends...";
		} else if (numFriends == 1) {
			title = getFirstFriend().getName();
		}
		mo.title(title);

		/*
		 * Set the marker's visuals (icon). If this marker is a cluster of multiple locations, use
		 * an orange version of the default GoogleMaps marker. Failing that, if this marker is a
		 * single location with multiple friends, use a blue version of the default GoogleMaps
		 * marker. For a single-friend, single-location marker, just use the default GoogleMaps
		 * marker.
		 * 
		 * BitmapDescriptor icon = null; if (locations.size() > 1) { icon =
		 * BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_ORANGE); } else if
		 * (numFriends > 1) { icon =
		 * BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_AZURE); } else { /* If
		 * there is only one friend at this location, load the profile pic from the profilePic URL
		 * asynchronously
		 * 
		 * String picURL = getFirstFriend().getProfilePicURL();
		 * 
		 * ResultCallback<Bitmap> callback = new ResultCallback<Bitmap>() {
		 * 
		 * public void onSuccess(Bitmap result) {
		 * 
		 * // On success of the callback, we should set the icon = to the loaded image icon =
		 * BitmapDescriptorFactory.fromBitmap(result);
		 * 
		 * }
		 * 
		 * public void onFailure(Throwable cause) {
		 * 
		 * } }; ImageLoaderTask ILT = new ImageLoaderTask(picURL, callback); // icon =
		 * BitmapDescriptorFactory.defaultMarker(); } mo.icon(icon);
		 */
		return mo;
	}

	/**
	 * Add this marker to the given map. If this marker had already been added to a map, it will be
	 * removed before being re-added.
	 * 
	 * MOVE TO MARKER MANAGER
	 * 
	 * @param toMap The map to add the marker.
	 */
	public void attach(GoogleMap toMap) {
		detach();
		marker = toMap.addMarker(getMarkerOptions());

	}

	/**
	 * Remove this marker if it has been attached to a map.
	 */
	public void detach() {
		if (marker != null) {
			marker.remove();
			marker = null;
		}
	}

	public void setIcon(BitmapDescriptor bitmap) {
		marker.setIcon(bitmap);

	}

	public int getNumLocationsAtMarker() {
		return locations.size();
	}

}
