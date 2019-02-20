package org.stepik.android.view.course_reviews.ui.adapter

import android.support.v7.util.DiffUtil
import org.stepic.droid.ui.custom.adapter_delegates.DelegateAdapter
import org.stepic.droid.ui.custom.adapter_delegates.DelegateViewHolder
import org.stepik.android.domain.course_reviews.model.CourseReviewItem
import org.stepik.android.model.user.User
import org.stepik.android.view.course_reviews.ui.adapter.delegates.CourseReviewDataDelegate
import org.stepik.android.view.course_reviews.ui.adapter.delegates.CourseReviewPlaceholderDelegate

class CourseReviewsAdapter(
    onUserClicked: (User) -> Unit
): DelegateAdapter<CourseReviewItem, DelegateViewHolder<CourseReviewItem>>() {
    var items: List<CourseReviewItem> = emptyList()
        set(value) {
            DiffUtil
                .calculateDiff(CourseReviewsDiffCallback(field, value))
                .dispatchUpdatesTo(this)
            field = value
        }

    init {
        addDelegate(CourseReviewDataDelegate(this, onUserClicked))
        addDelegate(CourseReviewPlaceholderDelegate(this))
    }

    override fun getItemAtPosition(position: Int): CourseReviewItem =
        items[position]

    override fun getItemCount(): Int =
        items.size
}