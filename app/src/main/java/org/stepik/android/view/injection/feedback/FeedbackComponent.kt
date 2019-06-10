package org.stepik.android.view.injection.feedback

import dagger.Subcomponent
import org.stepic.droid.ui.fragments.FeedbackFragment

interface FeedbackComponent {
    @Subcomponent.Builder
    interface Builder {
        fun build(): FeedbackComponent
    }

    fun inject(feedbackFragment: FeedbackFragment)
}