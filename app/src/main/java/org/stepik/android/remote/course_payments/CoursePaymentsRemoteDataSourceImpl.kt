package org.stepik.android.remote.course_payments

import android.os.Build
import io.reactivex.Completable
import org.solovyev.android.checkout.Purchase
import org.solovyev.android.checkout.Sku
import org.stepic.droid.web.StepicRestLoggedService
import org.stepik.android.data.course_payments.source.CoursePaymentsRemoteDataSource
import org.stepik.android.remote.course_payments.model.CoursePaymentRequest
import javax.inject.Inject

class CoursePaymentsRemoteDataSourceImpl
@Inject
constructor(
    private val loggedService: StepicRestLoggedService
) : CoursePaymentsRemoteDataSource {

    override fun createCoursePayment(courseId: Long, sku: Sku, purchase: Purchase): Completable =
        loggedService
            .createCoursePayment(
                CoursePaymentRequest(
                    course   = courseId,
                    provider = CoursePaymentRequest.Provider.GOOGLE,
                    data     = CoursePaymentRequest.Data(
                        token       = purchase.token,
                        packageName = purchase.packageName,
                        productId   = purchase.sku,
                        amount      = sku.detailedPrice.amount / 1_000_000f,
                        currency    = sku.detailedPrice.currency
                    )
                )
            )
}