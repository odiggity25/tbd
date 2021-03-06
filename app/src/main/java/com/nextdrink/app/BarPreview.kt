package com.nextdrink.app

import android.content.Context
import android.support.v7.widget.CardView
import android.text.Html
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import com.nextdrink.app.models.Bar
import com.nextdrink.app.utils.dpToPx
import com.nextdrink.app.utils.hoursFormattedString
import com.nextdrink.app.utils.matchesFilter

/**
 * Shows a preview of the barMeta and its deals in a card form
 * Created by orrie on 2017-07-04.
 */
class BarPreview(context: Context, width: Int, height: Int) : CardView(context) {
    val barName by lazy { findViewById(R.id.bar_name_shared) as TextView }
    val dealsDescription by lazy { findViewById(R.id.bar_preview_deals) as TextView}
    val barImage by lazy { findViewById(R.id.bar_image_shared) as ImageView }
    init {
        View.inflate(context, R.layout.view_bar_preview, this)
        layoutParams = LayoutParams(width, height)
        radius = dpToPx(6).toFloat()
        useCompatPadding = true
        isClickable = true
    }

    fun bind(bar: Bar, filter: DealFilter) {
        barName.text = bar.barMeta.name
        var dealText = ""
        bar.deals
                .filter { it.matchesFilter(filter) }
                .map {
                    dealText = dealText
                            .plus("<b>")
                            .plus(it.hoursFormattedString(context))
                            .plus("</b> ")
                            .plus(it.description)
                            .plus("<br>")
                }
        dealsDescription.text = Html.fromHtml(dealText)
        bar.barMeta.image?.let {
            barImage.setImageBitmap(it)
        }
    }
}