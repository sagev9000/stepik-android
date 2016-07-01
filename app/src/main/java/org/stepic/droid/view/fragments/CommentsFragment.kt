package org.stepic.droid.view.fragments

import android.os.Bundle
import android.support.design.widget.CoordinatorLayout
import android.support.design.widget.FloatingActionButton
import android.support.v4.app.Fragment
import android.support.v4.widget.SwipeRefreshLayout
import android.support.v7.app.AppCompatActivity
import android.support.v7.widget.LinearLayoutManager
import android.support.v7.widget.RecyclerView
import android.support.v7.widget.Toolbar
import android.text.SpannableString
import android.text.style.ForegroundColorSpan
import android.view.*
import android.widget.ProgressBar
import android.widget.Toast
import com.squareup.otto.Subscribe
import com.yandex.metrica.YandexMetrica
import org.stepic.droid.R
import org.stepic.droid.base.FragmentBase
import org.stepic.droid.base.MainApplication
import org.stepic.droid.core.CommentManager
import org.stepic.droid.events.comments.*
import org.stepic.droid.model.comments.Comment
import org.stepic.droid.model.comments.DiscussionOrder
import org.stepic.droid.model.comments.Vote
import org.stepic.droid.model.comments.VoteValue
import org.stepic.droid.util.AppConstants
import org.stepic.droid.util.ColorUtil
import org.stepic.droid.util.ProgressHelper
import org.stepic.droid.view.adapters.CommentsAdapter
import org.stepic.droid.view.dialogs.DeleteCommentDialogFragment
import org.stepic.droid.view.util.ContextMenuRecyclerView
import org.stepic.droid.web.DiscussionProxyResponse
import org.stepic.droid.web.VoteResponse
import retrofit.Callback
import retrofit.Response
import retrofit.Retrofit
import javax.inject.Inject

class CommentsFragment : FragmentBase(), SwipeRefreshLayout.OnRefreshListener {

    companion object {
        private val discussionIdKey = "dis_id_key"
        private val stepIdKey = "stepId"


        private val replyMenuId = 100
        private val likeMenuId = 101
        private val unLikeMenuId = 102
        private val reportMenuId = 103
        private val cancelMenuId = 104
        private val deleteMenuId = 105


        fun newInstance(discussionId: String, stepId: Long): Fragment {
            val args = Bundle()
            args.putString(discussionIdKey, discussionId)
            args.putLong(stepIdKey, stepId)
            val fragment = CommentsFragment()
            fragment.arguments = args
            return fragment
        }
    }

    @Inject
    lateinit var commentManager: CommentManager

    lateinit var commentAdapter: CommentsAdapter

    lateinit var discussionId: String
    var stepId: Long? = null

    lateinit var mToolbar: Toolbar
    lateinit var loadProgressBarOnCenter: ProgressBar
    lateinit var swipeRefreshLayout: SwipeRefreshLayout
    lateinit var recyclerView: RecyclerView
    lateinit var commentCoordinatorLayout: CoordinatorLayout
    var floatingActionButton: FloatingActionButton? = null
    lateinit var emptyStateView: View
    lateinit var errorView: View
    var needInsertOtUpdateLate: Comment? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        retainInstance = true
        MainApplication.component().inject(this)
        commentAdapter = CommentsAdapter(commentManager, context)
    }

    override fun onCreateView(inflater: LayoutInflater?, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val v = inflater?.inflate(R.layout.fragment_comments, container, false)
        setHasOptionsMenu(true)
        discussionId = arguments.getString(discussionIdKey)
        stepId = arguments.getLong(stepIdKey)
        setHasOptionsMenu(true)
        v?.let {
            initToolbar(v)
            initEmptyProgressOnCenter(v)
            initSwipeRefreshLayout(v)
            initRecyclerView(v)
            initAddCommentButton(v)
            initEmptyState(v)
            initConnectionError(v)
            commentCoordinatorLayout = v.findViewById(R.id.comments_coordinator_layout) as CoordinatorLayout
        }
        return v
    }

    private fun initConnectionError(v: View) {
        errorView = v.findViewById(R.id.report_problem)
    }

    private fun initEmptyState(v: View) {
        emptyStateView = v.findViewById(R.id.empty_comments)
    }

    private fun initAddCommentButton(v: View) {
        floatingActionButton = v.findViewById(R.id.add_new_comment_button) as FloatingActionButton
        floatingActionButton!!.setOnClickListener {
            if (stepId != null) {
                mShell.screenProvider.openNewCommentForm(activity, stepId, null)
            }
        }
    }

    private fun initRecyclerView(v: View) {
        recyclerView = v.findViewById(R.id.recycler_view_comments) as RecyclerView
        recyclerView.adapter = commentAdapter
        recyclerView.layoutManager = LinearLayoutManager(context)
        registerForContextMenu(recyclerView)
    }

    override fun onCreateContextMenu(menu: ContextMenu?, v: View?, menuInfo: ContextMenu.ContextMenuInfo?) {
        super.onCreateContextMenu(menu, v, menuInfo)
        menu?.setHeaderTitle(R.string.one_comment_title)

        val info = menuInfo as ContextMenuRecyclerView.RecyclerViewContextMenuInfo
        val position = info.position //resolve which should show
        val userId = mUserPreferences.userId
        val comment = commentManager.getItemWithNeedUpdatingInfoByPosition(position).comment
        if (userId > 0) {
            //it is not anonymous

            menu?.add(Menu.NONE, replyMenuId, Menu.NONE, R.string.reply_title)
            if (comment.user != null && comment.user.toLong() != userId && comment.vote != null) {
                //it is not current user and vote is available
                val vote = commentManager.getVoteByVoteId(comment.vote)
                if (vote?.value != null && vote?.value == VoteValue.like) {
                    //if we have like -> show suggest for unlike
                    menu?.add(Menu.NONE, unLikeMenuId, Menu.NONE, R.string.unlike_label)
                } else {
                    menu?.add(Menu.NONE, likeMenuId, Menu.NONE, R.string.like_label)
                }
                menu?.add(Menu.NONE, reportMenuId, Menu.NONE, R.string.report_label)
            }
            if (comment.actions?.delete ?: false) {
                val deleteText = getString(R.string.delete_label)
                val spannableString = SpannableString(deleteText);
                spannableString.setSpan(ForegroundColorSpan(ColorUtil.getColorArgb(R.color.feedback_bad_color)), 0, spannableString.length, 0)
                menu?.add(Menu.NONE, deleteMenuId, Menu.NONE, spannableString)
            }
        } else {
            //todo: Cancel only for anonymous?
            menu?.add(Menu.NONE, cancelMenuId, Menu.NONE, R.string.cancel)
        }
    }

    override fun onContextItemSelected(item: MenuItem?): Boolean {
        val info = item?.menuInfo as ContextMenuRecyclerView.RecyclerViewContextMenuInfo
        val position = info.position
        when (item?.itemId) {
            replyMenuId -> {
                replyToComment(info.position)
                return true
            }

            likeMenuId -> {
                likeComment(info.position)
                return true
            }

            unLikeMenuId -> {
                unlikeComment(info.position)
                return true
            }

            reportMenuId -> {
                abuseComment(info.position)
                return true
            }

            deleteMenuId -> {
                deleteComment(info.position)
                return true
            }

            else -> return super.onContextItemSelected(item)
        }
    }

    private fun deleteComment(position: Int) {
        YandexMetrica.reportEvent(AppConstants.DELETE_COMMENT_TRIAL)
        val comment: Comment? = commentManager.getItemWithNeedUpdatingInfoByPosition(position).comment
        val commentId = comment?.id
        commentId?.let {
            val dialog = DeleteCommentDialogFragment.Companion.newInstance(it)
            if (!dialog.isAdded) {
                dialog.show(fragmentManager, null)
            }
        }

    }

    private fun replyToComment(position: Int) {
        val comment: Comment? = commentManager.getItemWithNeedUpdatingInfoByPosition(position).comment
        comment?.let {
            mShell.screenProvider.openNewCommentForm(activity, stepId, it.parent ?: it.id)
        }
    }

    private fun likeComment(position: Int) {
        vote(position, VoteValue.like)
    }

    private fun unlikeComment(position: Int) {
        vote(position, null)
    }

    private fun abuseComment(position: Int) {
        vote(position, VoteValue.dislike)
    }

    private fun vote(position: Int, voteValue: VoteValue?) {

        val comment = commentManager.getItemWithNeedUpdatingInfoByPosition(position).comment
        val voteId = comment.vote
        val commentId = comment.id
        if (commentId == null) {
            YandexMetrica.reportEvent("comment: commentId null")
            return
        }
        voteId?.let {
            val voteObject = Vote(voteId, voteValue)
            mShell.api.makeVote(it, voteValue).enqueue(object : Callback<VoteResponse> {
                override fun onResponse(response: Response<VoteResponse>?, retrofit: Retrofit?) {
                    //todo event for update
                    if (response?.isSuccess ?: false) {
                        bus.post(LikeCommentSuccessEvent(commentId, voteObject))
                    } else {
                        YandexMetrica.reportEvent("comment: fail")
                        bus.post(LikeCommentFailEvent())
                    }

                }

                override fun onFailure(t: Throwable?) {
                    //todo event for fail
                    bus.post(LikeCommentFailEvent())
                }
            })
        }

    }

    @Subscribe
    fun onLikeCommentFail(event: LikeCommentFailEvent) {
        Toast.makeText(context, R.string.internet_problem, Toast.LENGTH_SHORT).show()
    }

    @Subscribe
    fun onLikeCommentSuccess(event: LikeCommentSuccessEvent) {
//        commentManager.insertOrUpdateVote(event.vote)
//        val position : Int = commentManager.getPositionOfComment(event.commentId)
//        if (position >= 0 && position< commentManager.getSize()){
//            commentAdapter.notifyItemChanged(position)
//        }
        //SO, comment count is not updated

        commentManager.loadCommentsByIds(longArrayOf(event.commentId))
        Toast.makeText(context, R.string.done, Toast.LENGTH_SHORT).show()
//        floatingActionButton?.let {
//            Snackbar.make(it, "Success!", Snackbar.LENGTH_SHORT)
//                    .setTextColor(ColorUtil.getColorArgb(R.color.white))
//                    .show()
//        }
    }


    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)
        bus.register(this)
        showEmptyProgressOnCenter()
        if (commentManager.isEmpty()) {
            loadDiscussionProxyById()
        } else {
            showEmptyProgressOnCenter(false)
        }
    }

    private fun initSwipeRefreshLayout(v: View) {
        swipeRefreshLayout = v.findViewById(R.id.swipe_refresh_layout_comments) as SwipeRefreshLayout
        swipeRefreshLayout.setOnRefreshListener(this)
        swipeRefreshLayout.setColorSchemeResources(
                R.color.stepic_brand_primary,
                R.color.stepic_orange_carrot,
                R.color.stepic_blue_ribbon)
    }

    private fun initEmptyProgressOnCenter(v: View) {
        loadProgressBarOnCenter = v.findViewById(R.id.load_progressbar) as ProgressBar
    }

    fun initToolbar(v: View) {
        mToolbar = v.findViewById(R.id.toolbar) as Toolbar
        (activity as AppCompatActivity).setSupportActionBar(mToolbar)
        (activity as AppCompatActivity).supportActionBar?.setDisplayHomeAsUpEnabled(true)
    }

    private fun loadDiscussionProxyById(id: String = discussionId) {
        mShell.api.getDiscussionProxies(id).enqueue(object : Callback<DiscussionProxyResponse> {
            override fun onResponse(response: Response<DiscussionProxyResponse>?, retrofit: Retrofit?) {
                if (response != null && response.isSuccess) {
                    val discussionProxy = response.body().discussionProxies.firstOrNull()
                    if (discussionProxy != null && discussionProxy.discussions.isNotEmpty()) {
                        bus.post(DiscussionProxyLoadedSuccessfullyEvent(discussionProxy))
                    } else {
                        bus.post(EmptyCommentsInDiscussionProxyEvent(id, discussionProxy))
                    }
                } else {
                    bus.post(InternetConnectionProblemInCommentsEvent(discussionId))
                }
            }

            override fun onFailure(t: Throwable?) {
                bus.post(InternetConnectionProblemInCommentsEvent(discussionId))
            }

        })
    }

    @Subscribe
    fun onInternetConnectionProblem(event: InternetConnectionProblemInCommentsEvent) {
        if (event.discussionProxyId.isNullOrBlank() || event.discussionProxyId == discussionId) {
            cancelSwipeRefresh()
            if (commentManager.isEmpty()) {
                showInternetConnectionProblem()
            } else {
                Toast.makeText(context, R.string.connectionProblems, Toast.LENGTH_SHORT).show()
                commentManager.clearAllLoadings()
                commentAdapter.notifyDataSetChanged()
            }
        }
    }

    @Subscribe
    fun onEmptyComments(event: EmptyCommentsInDiscussionProxyEvent) {
        cancelSwipeRefresh()
        if (event.discussionProxyId != discussionId) return;
        if (!commentManager.isEmpty()) {
            commentManager.resetAll(event.discussionProxy)
            commentAdapter.notifyDataSetChanged()
        }
        showEmptyState()
    }

    @Subscribe
    fun onDiscussionProxyLoadedSuccessfully(successfullyEvent: DiscussionProxyLoadedSuccessfullyEvent) {
        commentManager.setDiscussionProxy(successfullyEvent.discussionProxy)
        commentManager.loadComments()
    }


    @Subscribe
    fun onCommentsLoadedSuccessfully(successfullyEvent: CommentsLoadedSuccessfullyEvent) {
        cancelSwipeRefresh()
        showEmptyProgressOnCenter(false)
        showInternetConnectionProblem(false)
        if (!commentManager.isEmpty()) {
            showEmptyState(false)
        }
        val needInsertLocal: Comment? = needInsertOtUpdateLate
        if (needInsertLocal != null && (!commentManager.isCommentCached(needInsertLocal.id) || (needInsertLocal.parent != null && !commentManager.isCommentCached(needInsertLocal.parent)))) {
            val longArr = listOf(needInsertLocal.id, needInsertLocal.parent).filterNotNull().toLongArray()
            commentManager.loadCommentsByIds(longArr)
        } else {
            //we have only our comment.
            if (needInsertLocal != null) {
                commentManager.updateOnlyCommentsIfCachedSilent(listOf(needInsertLocal))
            }
            needInsertOtUpdateLate = null
            commentAdapter.notifyDataSetChanged()
        }
    }

    @Subscribe
    fun onNeedUpdate(needUpdateEvent: NewCommentWasAddedOrUpdateEvent) {
        if (needUpdateEvent.targetId == stepId) {
            if (needUpdateEvent.newCommentInsertOrUpdate != null) {
                //share for updating:
                needInsertOtUpdateLate = needUpdateEvent.newCommentInsertOrUpdate
            }
            //without animation.
            onRefresh() // it can be dangerous, when 10 or more comments was submit by another users.
        }
    }

    override fun onStart() {
        super.onStart()
    }

    override fun onStop() {
        super.onStop()
        cancelSwipeRefresh()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        bus.unregister(this)
        swipeRefreshLayout.setOnRefreshListener(null)
        floatingActionButton?.setOnClickListener(null)
        unregisterForContextMenu(recyclerView)
    }

    override fun onRefresh() {
        commentManager.reset()
        loadDiscussionProxyById()
    }

    private fun showEmptyProgressOnCenter(needShow: Boolean = true) {
        if (needShow) {
            ProgressHelper.activate(loadProgressBarOnCenter)
            showEmptyState(false)
            showInternetConnectionProblem(false)
        } else {
            ProgressHelper.dismiss(loadProgressBarOnCenter)
        }
    }

    private fun showEmptyState(isNeedShow: Boolean = true) {
        if (isNeedShow) {
            emptyStateView.visibility = View.VISIBLE
            showEmptyProgressOnCenter(false)
            showInternetConnectionProblem(false)
        } else {
            emptyStateView.visibility = View.GONE
        }
    }

    private fun showInternetConnectionProblem(needShow: Boolean = true) {
        if (needShow) {
            errorView.visibility = View.VISIBLE
            showEmptyState(false)
            showEmptyProgressOnCenter(false)
        } else {
            errorView.visibility = View.GONE
        }
    }

    private fun cancelSwipeRefresh() {
        ProgressHelper.dismiss(swipeRefreshLayout)
    }

    @Subscribe
    fun onFailDeleteComment(event: FailDeleteCommentEvent) {
        Toast.makeText(context, R.string.fail_delete_comment, Toast.LENGTH_SHORT).show()
    }

    override fun onCreateOptionsMenu(menu: Menu?, inflater: MenuInflater?) {
        inflater?.inflate(R.menu.coment_list_menu, menu)

        val defaultItem = menu?.findItem(mSharedPreferenceHelper.discussionOrder.menuId)
        defaultItem?.isChecked = true
    }

    override fun onOptionsItemSelected(item: MenuItem?): Boolean {

        DiscussionOrder.values().forEach {
            if (it.menuId.equals(item?.itemId)) {
                mSharedPreferenceHelper.discussionOrder = it
                item?.isChecked = true
                //todo reload comments, set to comment manager, and resolve above by id from preferences.
                return true;
            }
        }
        return super.onOptionsItemSelected(item)
    }

}