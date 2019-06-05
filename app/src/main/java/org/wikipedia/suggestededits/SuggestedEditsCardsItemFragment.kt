package org.wikipedia.suggestededits

import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.View.*
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.CompositeDisposable
import io.reactivex.schedulers.Schedulers
import kotlinx.android.synthetic.main.fragment_suggested_edits_cards_item.*
import kotlinx.android.synthetic.main.item_image_summary.view.*
import org.wikipedia.Constants.InvokeSource.*
import org.wikipedia.R
import org.wikipedia.dataclient.ServiceFactory
import org.wikipedia.dataclient.WikiSite
import org.wikipedia.page.PageTitle
import org.wikipedia.suggestededits.provider.MissingDescriptionProvider
import org.wikipedia.util.DateUtil
import org.wikipedia.util.L10nUtil.setConditionalLayoutDirection
import org.wikipedia.util.StringUtil
import org.wikipedia.util.log.L

class SuggestedEditsCardsItemFragment : Fragment() {
    private val disposables = CompositeDisposable()
    var sourceSummary: SuggestedEditsSummary? = null
    var targetSummary: SuggestedEditsSummary? = null
    var addedContribution: String = ""
        internal set
    var targetPageTitle: PageTitle? = null

    var pagerPosition = -1

    val title: String?
        get() = if (sourceSummary == null) null else sourceSummary!!.title

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        retainInstance = true
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        super.onCreateView(inflater, container, savedInstanceState)
        return inflater.inflate(R.layout.fragment_suggested_edits_cards_item, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setConditionalLayoutDirection(viewArticleContainer, parent().langFromCode)
        viewArticleImage.setLegacyVisibilityHandlingEnabled(true)
        cardItemErrorView.setBackClickListener { requireActivity().finish() }
        cardItemErrorView.setRetryClickListener {
            cardItemProgressBar.visibility = VISIBLE
            getArticleWithMissingDescription()
        }
        updateContents()
        if (sourceSummary == null) {
            getArticleWithMissingDescription()
        }

        cardView.setOnClickListener {
            if (sourceSummary != null) {
                parent().onSelectPage()
            }
        }
        showAddedContributionView(addedContribution)
    }

    override fun onDestroy() {
        super.onDestroy()
        disposables.clear()
    }

    private fun getArticleWithMissingDescription() {
        when (parent().source) {
            SUGGESTED_EDITS_TRANSLATE_DESC -> {
                disposables.add(MissingDescriptionProvider.getNextArticleWithMissingDescription(WikiSite.forLanguageCode(parent().langFromCode), parent().langToCode, true)
                        .subscribeOn(Schedulers.io())
                        .observeOn(AndroidSchedulers.mainThread())
                        .subscribe({ pair ->
                            sourceSummary = SuggestedEditsSummary(pair.second)
                            targetSummary = SuggestedEditsSummary(pair.first)
                            targetPageTitle = targetSummary!!.pageTitle
                            updateContents()
                        }, { this.setErrorState(it) })!!)
            }

            SUGGESTED_EDITS_ADD_CAPTION -> {
                // TODO: add image caption
            }

            SUGGESTED_EDITS_TRANSLATE_CAPTION -> {
                disposables.add(MissingDescriptionProvider.getNextImageWithMissingCaption(parent().langFromCode, parent().langToCode)
                        .subscribeOn(Schedulers.io())
                        .observeOn(AndroidSchedulers.mainThread())
                        .flatMap { pair ->
                            sourceSummary = SuggestedEditsSummary(pair.second, pair.first, parent().langFromCode)
                            targetSummary = SuggestedEditsSummary(pair.second, null, parent().langToCode)
                            targetPageTitle = targetSummary!!.pageTitle
                            ServiceFactory.get(WikiSite.forLanguageCode(parent().langFromCode)).getImageExtMetadata(pair.second.title())
                                    .subscribeOn(Schedulers.io())
                                    .observeOn(AndroidSchedulers.mainThread())
                        }
                        .subscribe({ response ->
                            val page = response.query()!!.pages()!![0]
                            if (page.imageInfo() != null && page.imageInfo()!!.metadata != null) {
                                sourceSummary!!.metadata = page.imageInfo()!!.metadata
                            }
                            updateContents()
                        }, { this.setErrorState(it) })!!)
            }

            else -> {
                disposables.add(MissingDescriptionProvider.getNextArticleWithMissingDescription(WikiSite.forLanguageCode(parent().langFromCode))
                        .subscribeOn(Schedulers.io())
                        .observeOn(AndroidSchedulers.mainThread())
                        .subscribe({ pageSummary ->
                            sourceSummary = SuggestedEditsSummary(pageSummary)
                            updateContents()
                        }, { this.setErrorState(it) }))
            }
        }
    }

    fun showAddedContributionView(addedContribution: String?) {
        if (!addedContribution.isNullOrEmpty()) {
            viewArticleSubtitleContainer.visibility = VISIBLE
            viewArticleSubtitle.text = addedContribution
            viewArticleExtract.maxLines = viewArticleExtract.maxLines - 1
            this.addedContribution = addedContribution
        }
    }

    private fun setErrorState(t: Throwable) {
        L.e(t)
        cardItemErrorView.setError(t)
        cardItemErrorView.visibility = VISIBLE
        cardItemProgressBar.visibility = GONE
        cardItemContainer.visibility = GONE
    }

    private fun updateContents() {
        val sourceAvailable = sourceSummary != null
        cardItemErrorView.visibility = GONE
        cardItemContainer.visibility = if (sourceAvailable) VISIBLE else GONE
        cardItemProgressBar.visibility = if (sourceAvailable) GONE else VISIBLE
        if (!sourceAvailable) {
            return
        }

        if (parent().source == SUGGESTED_EDITS_ADD_DESC || parent().source == SUGGESTED_EDITS_TRANSLATE_DESC) {
            updateDescriptionContents()
        } else {
            updateCaptionContents()
        }
    }

    private fun updateDescriptionContents() {
        viewArticleTitle.text = sourceSummary!!.normalizedTitle

        if (parent().source == SUGGESTED_EDITS_TRANSLATE_DESC) {
            viewArticleSubtitleContainer.visibility = VISIBLE
            viewArticleSubtitle.text = (if (addedContribution.isNotEmpty()) addedContribution else sourceSummary!!.description)?.capitalize()
        }

        viewImageSummaryContainer.visibility = GONE

        viewArticleExtract.text = StringUtil.fromHtml(sourceSummary!!.extractHtml)
        if (sourceSummary!!.thumbnailUrl.isNullOrBlank()) {
            viewArticleImage.visibility = GONE
            viewArticleExtract.maxLines = ARTICLE_EXTRACT_MAX_LINE_WITHOUT_IMAGE
        } else {
            viewArticleImage.visibility = VISIBLE
            viewArticleImage.loadImage(Uri.parse(sourceSummary!!.thumbnailUrl))
            viewArticleExtract.maxLines = ARTICLE_EXTRACT_MAX_LINE_WITH_IMAGE
        }
    }

    private fun updateCaptionContents() {
        viewArticleTitle.text = sourceSummary!!.normalizedTitle

        if (parent().source == SUGGESTED_EDITS_TRANSLATE_CAPTION) {
            viewArticleSubtitleContainer.visibility = VISIBLE
            viewArticleSubtitle.text = (if (addedContribution.isNotEmpty()) addedContribution else sourceSummary!!.description)?.capitalize()
        }

        // TODO: programmatically add the strings or add static views into the parent view.
        val titleResourcesArray = arrayOf(
                if (sourceSummary!!.user.isNullOrEmpty())
                    R.string.suggested_edits_image_caption_summary_title_artist
                else
                    R.string.suggested_edits_image_caption_summary_title_author,
                R.string.suggested_edits_image_caption_summary_title_date,
                R.string.suggested_edits_image_caption_summary_title_source,
                R.string.suggested_edits_image_caption_summary_title_license
        )

        val contentArray = arrayOf(
                if (sourceSummary!!.user.isNullOrEmpty())
                    sourceSummary!!.metadata!!.artist()!!.value()
                else
                    sourceSummary!!.user,
                DateUtil.getReadingListsLastSyncDateString(sourceSummary!!.timestamp!!),
                sourceSummary!!.metadata!!.credit()!!.value(),
                sourceSummary!!.metadata!!.licenseShortName()!!.value()
        )

        contentArray.forEachIndexed { i, content ->
            val summaryItemView = inflate(requireContext(), R.layout.item_image_summary, null)
            summaryItemView.summaryTitle.text = getString(titleResourcesArray[i])
            summaryItemView.summaryContent.text = StringUtil.removeHTMLTags(content!!)
            viewImageSummaryContainer.addView(summaryItemView)
        }

        viewArticleImage.loadImage(Uri.parse(sourceSummary!!.thumbnailUrl))
        viewArticleExtract.visibility = GONE
    }

    private fun parent(): SuggestedEditsCardsFragment {
        return requireActivity().supportFragmentManager.fragments[0] as SuggestedEditsCardsFragment
    }

    companion object {
        const val ARTICLE_EXTRACT_MAX_LINE_WITH_IMAGE = 5
        const val ARTICLE_EXTRACT_MAX_LINE_WITHOUT_IMAGE = 12

        fun newInstance(): SuggestedEditsCardsItemFragment {
            return SuggestedEditsCardsItemFragment()
        }
    }
}
