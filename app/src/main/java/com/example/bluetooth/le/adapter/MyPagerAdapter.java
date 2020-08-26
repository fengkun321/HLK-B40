package com.example.bluetooth.le.adapter;

import android.support.v4.view.PagerAdapter;
import android.support.v4.view.ViewPager;
import android.view.View;

import java.util.ArrayList;
import java.util.List;

public class MyPagerAdapter extends PagerAdapter {

	private List<View> list = new ArrayList<View>();

	public MyPagerAdapter(List<View> l) {
		this.list = l;
	}
	
	public int getCount() {
		return list.size();
	}

	@Override
	public boolean isViewFromObject(View arg0, Object arg1) {
		return arg0 == arg1;
	}
	
	public void destroyItem(View container, int position, Object object) {
		((ViewPager) container).removeView(list.get(position));
	}
	
	public Object instantiateItem(View container, int position) {
		((ViewPager)container).addView(list.get(position),0);
		return list.get(position);
	}

}
