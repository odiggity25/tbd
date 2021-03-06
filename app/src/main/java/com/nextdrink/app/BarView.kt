package com.nextdrink.app

import android.app.AlertDialog
import android.content.Context
import android.support.constraint.ConstraintLayout
import android.support.constraint.ConstraintSet
import android.support.design.widget.Snackbar
import android.support.v7.widget.LinearLayoutManager
import android.support.v7.widget.RecyclerView
import android.view.View
import android.widget.*
import com.jakewharton.rxbinding2.view.clicks
import com.jakewharton.rxbinding2.view.detaches
import com.nextdrink.app.models.Bar
import com.nextdrink.app.models.CollapsedBarViewData
import com.nextdrink.app.models.Deal
import com.nextdrink.app.models.DealReport
import com.nextdrink.app.report.ReportDealView
import com.nextdrink.app.utils.view.StarRatingBar
import io.reactivex.Observable
import io.reactivex.subjects.PublishSubject

/**
 * Created by orrie on 2017-07-10.
 */
class BarView : ConstraintLayout {

    private val dealClicksSubject = PublishSubject.create<Deal>()
    val dealClicks: Observable<Deal> = dealClicksSubject.hide()
    private val reportDealClicksSubject = PublishSubject.create<Deal>()
    val reportDealClicks: Observable<Deal> = reportDealClicksSubject.hide()
    private val reportDealSubmitsSubject = PublishSubject.create<DealReport>()
    val reportDealSubmits: Observable<DealReport> = reportDealSubmitsSubject.hide()

    private val starRatingLayout by lazy { findViewById(R.id.bar_view_stars) }
    private val starRatingText by lazy { findViewById(R.id.bar_view_stars_text) as TextView }
    private val starRatingBar by lazy { findViewById(R.id.bar_view_star_rating_bar) as StarRatingBar }
    private val priceLevel by lazy { findViewById(R.id.bar_view_price_level) as TextView }
    private val websiteButton by lazy { findViewById(R.id.bar_view_website_button) }
    val websiteClicks by lazy { findViewById(R.id.bar_view_website_button).clicks() }
    val showOnMapClicks by lazy { findViewById(R.id.bar_view_map_button).clicks() }

    var reportDealDialog: AlertDialog? = null

    constructor(context: Context) : super(context) {
        View.inflate(context, R.layout.view_bar, this)
    }

    constructor(context: Context, id: Int, bar: Bar, collapsedData: CollapsedBarViewData): super(context) {
        this.id = id
        View.inflate(context, R.layout.view_bar, this)
        setBackgroundResource(R.color.white)
        BarViewPresenter(context, bar, this)

        layoutParams = FrameLayout.LayoutParams(collapsedData.width, collapsedData.height)
        x = collapsedData.x
        y = collapsedData.y
        visibility = View.INVISIBLE

        val collapsedConstraint = ConstraintSet()
        collapsedConstraint.clone(context, R.layout.view_bar_collapsed)
        setConstraintSet(collapsedConstraint)

        (findViewById(R.id.bar_image_shared) as ImageView).setImageBitmap(collapsedData.barImage)
        (findViewById(R.id.bar_name_shared) as TextView).text = collapsedData.barName

        val recyclerView = findViewById(R.id.bar_deals_list) as RecyclerView
        val barDealsAdapter = BarDealsAdapter(context, bar)
        recyclerView.adapter = barDealsAdapter
        barDealsAdapter.dealClicks.subscribe { dealClicksSubject.onNext(it) }
        recyclerView.layoutManager = LinearLayoutManager(context)

        websiteButton.visibility = if (bar.barMeta.website != null) View.VISIBLE else View.GONE

        if (bar.barMeta.rating != null) {
            starRatingLayout.visibility = View.VISIBLE
            starRatingBar.bind(bar.barMeta.rating)
            val ratingString = String.format("%.2f", bar.barMeta.rating)
            starRatingText.text = ratingString
        } else {
            starRatingLayout.visibility = View.GONE
        }

        if (bar.barMeta.priceLevel != null) {
            priceLevel.visibility = View.VISIBLE
            var dollarSigns = ""
            for (i in 1..bar.barMeta.priceLevel) {
                dollarSigns = dollarSigns.plus("$")
            }
            priceLevel.text = dollarSigns
        } else {
            priceLevel.visibility = View.GONE
        }

        detaches().subscribe { reportDealDialog?.dismiss() }
    }

    fun showDealOptions(deal: Deal) {
        val arrayAdapter = ArrayAdapter<String>(context, android.R.layout.select_dialog_item)
        arrayAdapter.add(context.getString(R.string.report_inaccuracy))
        AlertDialog.Builder(context)
                .setAdapter(arrayAdapter) { _, which ->
                    when(arrayAdapter.getItem(which)) {
                        context.getString(R.string.report_inaccuracy) -> {
                            reportDealClicksSubject.onNext(deal)
                        }
                    }
                }
                .setTitle(deal.description)
                .show()
    }

    fun showReportDealDialog(deal: Deal) {
        reportDealDialog = AlertDialog.Builder(context)
                .setView(ReportDealView(context, deal))
                .setPositiveButton(R.string.submit) { _, _ ->
                    var reportInfo = reportDealDialog?.let {
                                                (it.findViewById(R.id.report_deal_info) as EditText).text.toString()
                                            }
                    reportInfo = if (reportInfo == null) "" else reportInfo
                    reportDealSubmitsSubject.onNext(DealReport(deal, reportInfo))
                    Snackbar.make(this, context.getString(R.string.report_reviewed_shortly), Snackbar.LENGTH_LONG).show()
                }
                .setNegativeButton(R.string.cancel, null)
                .create()

        reportDealDialog?.show()
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        reportDealDialog?.dismiss()
    }

}