package com.nextdrink.app

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.content.Context
import android.support.constraint.ConstraintLayout
import android.support.v4.app.FragmentManager
import android.support.v4.graphics.drawable.DrawableCompat
import android.support.v7.app.AppCompatActivity
import android.transition.TransitionManager
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.animation.AccelerateInterpolator
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import com.google.android.gms.maps.*
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.Marker
import com.google.android.gms.maps.model.MarkerOptions
import com.jakewharton.rxbinding2.view.clicks
import com.jakewharton.rxbinding2.view.detaches
import com.nextdrink.app.models.Bar
import com.nextdrink.app.models.CollapsedBarViewData
import com.nextdrink.app.moderate.ModerateActivity
import com.nextdrink.app.utils.animation.slideTransitionCompat
import com.nextdrink.app.utils.dpToPx
import com.nextdrink.app.utils.hideKeyboard
import com.nextdrink.app.utils.matchesFilter
import com.nextdrink.app.utils.pxToDp
import com.nextdrink.app.utils.view.throttleClicks
import com.wattpad.tap.util.onNextLayout
import io.reactivex.Observable
import io.reactivex.subjects.PublishSubject
import java.util.concurrent.TimeUnit




/**
 * Created by orrie on 2017-06-19.
 */
class MainView(context: Context,
               supportFragmentManager: FragmentManager) : FrameLayout(context), OnMapReadyCallback {

    private var googleMap: GoogleMap? = null
    private val mapFragment by lazy { supportFragmentManager.findFragmentById(R.id.map) as SupportMapFragment }
    val addBarClicks by lazy { findViewById(R.id.main_add_bar).throttleClicks() }
    val moderateClicks by lazy { findViewById(R.id.main_moderate).throttleClicks() }
    private val addImage by lazy { findViewById(R.id.main_add_bar) as ImageView }
    private var addDealView: AddDealView? = null
    private var dealFiltersView: DealFiltersView? = null
    private val container by lazy { findViewById(R.id.main_container) as FrameLayout }
    private val barListView by lazy { findViewById(R.id.deal_list_view) as BarListView }
    private val dayOfWeekPicker by lazy { findViewById(R.id.main_day_of_week_picker) as DayOfWeekPicker }
    val dayClicks: Observable<DayOfWeekPicker.DaySelected> by lazy { dayOfWeekPicker.dayClicks }
    private val filterDealsButton by lazy { findViewById(R.id.main_filter_deals) as ImageView }
    val filterClicks by lazy { filterDealsButton.clicks() }
    private val filterClosesSubject = PublishSubject.create<DealFilter>()
    val filterCloses: Observable<DealFilter> = filterClosesSubject.hide()

    private val mapReadiesSubject = PublishSubject.create<Unit>()
    val mapReadies: Observable<Unit> = mapReadiesSubject.hide()
    private val mapChangesSubject = PublishSubject.create<Projection>()
    val mapChanges: Observable<Projection> = mapChangesSubject.hide()
    private val addBarDialogShowsSubject = PublishSubject.create<Unit>()
    val addBarDialogShows: Observable<Unit> = addBarDialogShowsSubject.hide()
    private val addBarDialogClosesSubject = PublishSubject.create<Unit>()
    val addBarDialogCloses: Observable<Unit> = addBarDialogClosesSubject.hide()
    private val markerClicksSubject = PublishSubject.create<String>()
    val markerClicks: Observable<String> = markerClicksSubject.hide()

    // Variables used for the transition to the [BarView]
    var barPreview: ConstraintLayout? = null
    val barViewId = 5474138
    var collapsedData = CollapsedBarViewData()
    var barViewTransition: BarViewTransition? = null

    private val bars = mutableListOf<Bar>()
    var dealFilter = DealFilter()
    var lastOpened: Marker? = null
    val markers = mutableListOf<Marker>()
    val mainPresenter: MainPresenter
    private var position: LatLng? = null

    init {
        View.inflate(context, R.layout.view_main, this)
        layoutParams = LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        mapFragment.getMapAsync(this)
        addBarClicks.throttleFirst(500, TimeUnit.MILLISECONDS)
                .subscribe { if (addImage.rotation == 0f) addBarDialogShowsSubject.onNext(Unit)
                else addBarDialogClosesSubject.onNext(Unit) }
        mainPresenter = MainPresenter(this, detaches(), barListView)
        findViewById(R.id.main_moderate).visibility = if (BuildConfig.DEBUG) View.VISIBLE else View.GONE
    }

    fun bind(filter: DealFilter) {
        this.dealFilter = filter
        barListView.bind(filter)
    }

    override fun onMapReady(googleMap: GoogleMap) {
        this.googleMap = googleMap
        mapReadiesSubject.onNext(Unit)
        googleMap.setOnCameraIdleListener {
            googleMap.let { mapChangesSubject.onNext(it.projection) }
        }

        // Custom marker listener seems to be the only way to prevent the map from centering on marker click
        googleMap.setOnMarkerClickListener({ marker ->
            if (lastOpened != marker) {
                lastOpened?.hideInfoWindow()
            }
            marker.showInfoWindow()
            lastOpened = marker
            markerClicksSubject.onNext(marker.tag as String)
            true
        })
    }

    fun moveMap(latLng: LatLng, zoom: Float) {
        googleMap?.moveCamera(CameraUpdateFactory.newLatLngZoom(latLng, zoom))
    }

    fun showAddBarView() {
        val addDealView = AddDealView(context)
        addDealView.closes.subscribe { closeAddBarView() }
        this.addDealView = addDealView
        container.addView(addDealView)
        addDealView.x = width.toFloat()
        addDealView.y = -height.toFloat()
        addDealView.animate()
                .x(0f)
                .y(0f)
                .setDuration(300)
                .setListener(null)

        addImage.bringToFront()
        addImage.animate()
                .rotation(135f)
                .setDuration(200)
                .x(addImage.x - pxToDp(50))
                .y(addImage.y + pxToDp(50))
                .setListener(object : AnimatorListenerAdapter() {
                    override fun onAnimationEnd(animation: Animator?) {
                        addImage.rotation = 45f
                    }
                })
    }

    fun closeAddBarView() {
        hideKeyboard(context as AppCompatActivity)
        addImage.rotation = 45f
        addImage.animate()
                .rotation(-180f)
                .x(addImage.x + pxToDp(50))
                .y(addImage.y - pxToDp(50))
                .setDuration(200)
                .setListener(object : AnimatorListenerAdapter() {
                    override fun onAnimationEnd(animation: Animator?) {
                        addImage.rotation = 0f
                    }
                })

        addDealView?.let {
            it.animate()
                    .x(width.toFloat())
                    .y(-height.toFloat())
                    .setDuration(300)
                    .setListener(object : AnimatorListenerAdapter() {
                        override fun onAnimationEnd(animation: Animator?) {
                            container.removeView(addDealView)
                            addDealView = null
                        }
                    })
                    .setInterpolator(AccelerateInterpolator())
        }
    }

    fun showBarView(pair: Pair<Bar, ConstraintLayout>) {
        recordCollapsedData(pair)

        //This will create a collapsed version of the BarView with the bar photo and title in the same place as the
        // BarPreviewView so we can then animate it to the full version
        val barView = BarView(context, barViewId, pair.first, collapsedData)

        // For some reason the transitions won't work if you try adding the view and then changing the bounds
        // and location, it just does it all at once. So to work around it I'm adding the view first as invisible
        // requesting a layout and then animating the visibility in and changing the bounds
        addView(barView)
        barView.clicks().subscribe { hideBarView() }
        requestLayout()
        onNextLayout {
            barViewTransition = BarViewTransition(context, this, barView, pair.second, collapsedData)
            barViewTransition?.transition(true)
        }
    }

    /**
     * Records info about the BarPreview view so we can place the collapsed BarView over top and animate
     * it into the full BarView
     */
    fun recordCollapsedData(pair: Pair<Bar, ConstraintLayout>) {
        val barPreview = pair.second
        this.barPreview = barPreview
        val location = intArrayOf(0, 0)

        barPreview.getLocationInWindow(location)
        val barPreviewName = barPreview.findViewById(R.id.bar_name_shared) as TextView
        val bitmap = pair.first.barMeta.image
        collapsedData = CollapsedBarViewData(
                location[0].toFloat(),
                location[1].toFloat() - dpToPx(25),
                barPreview.width,
                barPreview.height,
                pxToDp(barPreviewName.textSize.toInt()).toFloat(),
                barPreviewName.text.toString(),
                bitmap)
    }

    fun hideBarView() {
        val barView: ConstraintLayout? = findViewById(barViewId) as ConstraintLayout
        barView?.let { barViewTransition?.transition(false) }
    }

    fun addBar(bar: Bar) {
        bars.add(bar)

        if (!bar.deals.filter { it.matchesFilter(dealFilter) }.isEmpty()) {
            addMarker(bar)
        }
    }

    private fun addMarker(bar: Bar) {
        val marker = googleMap?.addMarker(MarkerOptions()
                .position(LatLng(bar.barMeta.lat, bar.barMeta.lng))
                .title(bar.barMeta.name))
        marker?.let {
            it.tag = bar.barMeta.id
            markers.add(it)
        }
    }

    fun setPositionMarker(latLng: LatLng) {
        position = latLng
        addPositionMarker()
    }
    fun addPositionMarker() {
        position?.let {
            googleMap?.addMarker(MarkerOptions()
                    .position(it)
                    .icon(BitmapDescriptorFactory.fromResource(R.drawable.blue_location))
                    .zIndex(1f)
            )
        }
    }

    fun removeBar(bar: Bar) {
        val marker = markers.firstOrNull { it.tag == bar.barMeta.id }
        marker?.let {
            it.remove()
            markers.remove(it)
        }
        val position = bars.indexOfFirst { it.barMeta.id == bar.barMeta.id }
        if (position >= 0) {
            bars.removeAt(position)
        }
    }

    fun showMarkerInfoWindow(barId: String) {
        lastOpened?.let {
            if (it.tag == barId && it.isInfoWindowShown) return
            it.hideInfoWindow()
        }
        markers.filter { it.tag == barId }
                .map {
                    it.showInfoWindow()
                    lastOpened = it
                }
    }

    fun filterMarkers() {
        googleMap?.clear()
        markers.clear()
        addPositionMarker()
        val barsFiltered = bars.filter { !it.deals.filter { it.matchesFilter(dealFilter) }.isEmpty() } as MutableList<Bar>
        barsFiltered.forEach { addMarker(it) }
    }

    fun showModerateActivity() {
        context.startActivity(ModerateActivity.newIntent(context))
    }

    fun updateFilter(dealFilter: DealFilter) {
        this.dealFilter = dealFilter
        filterMarkers()
        barListView.filter(dealFilter)
        val drawable = DrawableCompat.wrap(filterDealsButton.drawable)
        DrawableCompat.setTint(drawable, context.resources.getColor(
                if (dealFilter.tags.isEmpty()) R.color.medium_grey else R.color.colorAccent
        ))
    }

    fun setInitialDayOfWeekToNow() {
        dayOfWeekPicker.setInitialDay(7)
    }

    fun onBackPressed(): Boolean {
        if (findViewById(barViewId) !== null) {
            hideBarView()
            return true
        } else if (addDealView != null) {
            closeAddBarView()
            return true
        } else if (dealFiltersView != null) {
            hideDealFiltersView()
            return true
        }
        return false
    }

    fun showDealFiltersView(dealFilter: DealFilter) {
        dealFiltersView = DealFiltersView(context, dealFilter)
        dealFiltersView?.closes?.subscribe { filterClosesSubject.onNext(it) }

        val slideTransition = slideTransitionCompat(Gravity.BOTTOM)
                .addTarget(dealFiltersView)
        TransitionManager.beginDelayedTransition(this, slideTransition)
        addView(dealFiltersView)
    }

    fun hideDealFiltersView() {
        val slideTransition = slideTransitionCompat(Gravity.BOTTOM)
                .addTarget(dealFiltersView)
        TransitionManager.beginDelayedTransition(this, slideTransition)
        removeView(dealFiltersView)
    }

}