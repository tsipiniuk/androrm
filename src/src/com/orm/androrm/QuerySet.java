/**
 * 	Copyright (c) 2010 Philipp Giese
 * 
 * Permission is hereby granted, free of charge, to any person obtaining
 * a copy of this software and associated documentation files (the
 * "Software"), to deal in the Software without restriction, including
 * without limitation the rights to use, copy, modify, merge, publish,
 * distribute, sublicense, and/or sell copies of the Software, and to
 * permit persons to whom the Software is furnished to do so, subject to
 * the following conditions:
 * 
 * The above copyright notice and this permission notice shall be
 * included in all copies or substantial portions of the Software.
 * 
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND,
 * EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF
 * MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND
 * NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE
 * LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION
 * OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION
 * WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */
package com.orm.androrm;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;

import android.content.Context;
import android.database.Cursor;
import android.util.Log;

/**
 * @author Philipp Giese
 */
public class QuerySet<T extends Model> implements Iterable<T> {

	private static final String TAG = "ANDRORM:QUERY:SET";
	
	private SelectStatement mQuery;
	private Class<T> mClass;
	private List<T> mItems;
	private DatabaseAdapter mAdapter;
	
	public QuerySet(Context context, Class<T> model) {
		mClass = model;
		mAdapter = new DatabaseAdapter(context);
	}
	
	protected void injectQuery(SelectStatement query) {
		mQuery = query;
	}
	
	private Cursor getCursor(SelectStatement query) {
		mAdapter.open();
		
		return mAdapter.query(query);
	}
	
	private void closeConnection(Cursor c) {
		c.close();
		mAdapter.close();
	}
	
	public T get(int id) {
		Where where = new Where();
		where.setStatement(new Statement("mId", id));
		
		if(mQuery == null) {
			mQuery = new SelectStatement();
			mQuery.from(DatabaseBuilder.getTableName(mClass))
				  .where(where);
		} else {
			mQuery.where(where);
		}
		
		Cursor c = getCursor(mQuery);
		T object = createObject(c);
		closeConnection(c);
		
		return object;
	}
	
	public QuerySet<T> orderBy(String... columns) {
		if(mQuery != null) {
			mQuery.orderBy(columns);
		}
		
		return this;
	}
	
	public QuerySet<T> distinct() {
		if(mQuery != null) {
			mQuery.distinct();
		}
		
		return this;
	}

	public QuerySet<T> all() {
		if(mQuery == null) {
			mQuery = new SelectStatement();
			mQuery.from(DatabaseBuilder.getTableName(mClass));
		}
		
		return this;
	}
	
	public QuerySet<T> filter(Filter filter) {
		SelectStatement query = null;
		
		try {
			query = QueryBuilder.buildQuery(mClass, filter.getRules(), 0);
		} catch (NoSuchFieldException e) {
			Log.e(TAG, "could not build query for filter", e);
		}
		
		if(mQuery == null) {
			mQuery = query;
		} else {
			JoinStatement join = new JoinStatement();
			join.left(mQuery, "left")
				.right(query, "right")
				.on("mId", "mId");
			
			SelectStatement select = new SelectStatement();
			select.from(join);
			
			mQuery = select;
		}
		
		return this;
	}
	
	public QuerySet<T> limit(int limit) {
		return limit(new Limit(limit));
	}
	
	public QuerySet<T> limit(int offset, int limit) {
		return limit(new Limit(offset, limit));
	}
	
	public QuerySet<T> limit(Limit limit) {
		if(mQuery != null) {
			mQuery.limit(limit);
		}
		
		return this;
	}
	
	public int count() {
		if(mQuery != null) {
			SelectStatement query = new SelectStatement();
			query.from(mQuery)
				 .count();
			
			Cursor c = getCursor(query);
			
			int count = 0;
			
			if(c.moveToNext()) {
				count = c.getInt(c.getColumnIndexOrThrow(Model.COUNT));
			}
			
			closeConnection(c);
			
			return count;
		}
		
		return all().count();
	}
	
	private T createObject(Cursor c) {
		T object = null;
		
		if(c.moveToNext()) {
			object = Model.createObject(mClass, c);
		}
		
		return object;
	}
	
	private List<T> createObjects(Cursor c) {
		List<T> items = new ArrayList<T>();
		
		while(c.moveToNext()) {
			T object = Model.createObject(mClass, c);
			
			if(object != null) {
				items.add(object);
			}
		}
		
		return items;
	}
	
	private List<T> getItems() {
		if(mItems == null) {
			mItems = new ArrayList<T>();
			
			if(mQuery != null) {
				Cursor c = getCursor(mQuery);
				mItems.addAll(createObjects(c));
				closeConnection(c);
			}
		}
		
		return mItems;
	}
	
	@Override
	public Iterator<T> iterator() {
		return getItems().iterator();
	}

	/**
	 * Checks if the result of this query contains the given 
	 * object. Note, that this operation will execute the query
	 * on the database. Use only, if you have to. 
	 * 
	 * @param object
	 * @return
	 */
	public boolean contains(T object) {
		return getItems().contains(object);
	}

	/**
	 * See {@link QuerySet#contains}
	 * @param arg0
	 * @return
	 */
	public boolean containsAll(Collection<T> arg0) {
		return getItems().containsAll(arg0);
	}

	public boolean isEmpty() {
		return count() == 0;
	}

}
