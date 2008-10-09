/*
	Copyright (C) 2008 Jeffrey Sharkey, http://jsharkey.org/
	
	This program is free software: you can redistribute it and/or modify
	it under the terms of the GNU General Public License as published by
	the Free Software Foundation, either version 3 of the License, or
	(at your option) any later version.
	
	This program is distributed in the hope that it will be useful,
	but WITHOUT ANY WARRANTY; without even the implied warranty of
	MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
	GNU General Public License for more details.
	
	You should have received a copy of the GNU General Public License
	along with this program.  If not, see <http://www.gnu.org/licenses/>.
*/

package org.tunescontrol.util;

import android.content.SearchRecentSuggestionsProvider;

public class RecentProvider extends SearchRecentSuggestionsProvider {

	public final static String AUTHORITY = "org.tunescontrol.recent";
	public final static int MODE = DATABASE_MODE_QUERIES;

	public RecentProvider() {
		super();
		setupSuggestions(AUTHORITY, MODE);
	}
}
