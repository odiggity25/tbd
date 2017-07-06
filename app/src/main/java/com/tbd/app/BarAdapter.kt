package com.tbd.app

import android.content.Context
import android.support.v7.widget.RecyclerView
import android.view.ViewGroup
import com.jakewharton.rxbinding2.view.clicks
import com.tbd.app.models.Bar
import com.tbd.app.utils.dpToPx
import io.reactivex.Observable
import io.reactivex.subjects.PublishSubject

/**
 * Created by orrie on 2017-07-04.
 */
class BarAdapter(private val context: Context,
                 private val bars: MutableList<Bar>) : RecyclerView.Adapter<BarAdapter.BarHolder>() {

    private val barClicksSubject = PublishSubject.create<String>()
    val barClicks: Observable<String> = barClicksSubject.hide()

    override fun onBindViewHolder(holder: BarHolder?, position: Int) {
        holder?.view?.bind(bars[position])
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): BarHolder {
        // -30 so the next item peeks in
        val barHolder = BarHolder(BarView(context, parent.measuredWidth - dpToPx(30)))
        barHolder.view.clicks().subscribe {
            val barId = bars[barHolder.adapterPosition].barMeta.id
            barClicksSubject.onNext(barId)
        }
        return barHolder
    }

    override fun getItemCount(): Int {
        return bars.size
    }

    fun getItem(position: Int): Bar =
            bars[position]

    fun getPosition(barId: String): Int =
        bars.indexOfFirst { it.barMeta.id == barId }

    fun addItem(bar: Bar) {
        bars.add(bar)
        notifyItemInserted(bars.lastIndex)
    }

    fun removeItem(barToRemove: Bar) {
        var indexToRemove = bars.indexOfFirst { it.barMeta.id == barToRemove.barMeta.id }
        if (indexToRemove >= 0) {
            bars.removeAt(indexToRemove)
            notifyItemRemoved(indexToRemove)
        }
    }

    class BarHolder(val view: BarView) : RecyclerView.ViewHolder(view)
}