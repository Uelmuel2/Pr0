package com.pr0gramm.app.feed;

import com.google.common.base.Optional;
import com.google.common.base.Strings;
import com.pr0gramm.app.Settings;
import com.pr0gramm.app.Stats;
import com.pr0gramm.app.api.categories.ExtraCategoryApi;
import com.pr0gramm.app.api.categories.ExtraCategoryApiProvider;
import com.pr0gramm.app.api.pr0gramm.Api;
import com.pr0gramm.app.services.Track;

import org.immutables.value.Value;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Set;

import javax.inject.Inject;
import javax.inject.Singleton;

import rx.Observable;

/**
 * Performs the actual request to get the items for a feed.
 */
@Singleton
public class FeedService {
    private static final Logger logger = LoggerFactory.getLogger("FeedService");

    private final Api mainApi;
    private final ExtraCategoryApi categoryApi;
    private final Settings settings;

    @Inject
    public FeedService(Api mainApi, ExtraCategoryApiProvider categoryApi, Settings settings) {
        this.mainApi = mainApi;
        this.categoryApi = categoryApi.get();
        this.settings = settings;
    }

    public Observable<Api.Feed> getFeedItems(FeedQuery query) {
        FeedFilter feedFilter = query.feedFilter();
        Track.requestFeed(feedFilter.getFeedType());

        // filter by feed-type
        Integer promoted = (feedFilter.getFeedType() == FeedType.PROMOTED) ? 1 : null;
        Integer following = (feedFilter.getFeedType() == FeedType.PREMIUM) ? 1 : null;

        int flags = ContentType.combine(query.contentTypes());
        String tags = feedFilter.getTags().orNull();
        String user = feedFilter.getUsername().orNull();

        // FIXME this is quite hacky right now.
        String likes = feedFilter.getLikes().orNull();
        Boolean self = Strings.isNullOrEmpty(likes) ? null : true;

        FeedType feedType = query.feedFilter().getFeedType();

        // statistics
        Stats.get().incrementCounter("feed.loaded", "type:" + feedType.name().toLowerCase());

        switch (feedType) {
            case RANDOM:
                return categoryApi.random(tags, flags);

            case BESTOF:
                int benisScore = settings.bestOfBenisThreshold();
                return categoryApi.bestof(tags, user, flags, query.older().orNull(), benisScore);

            case CONTROVERSIAL:
                return categoryApi.controversial(tags, flags, query.older().orNull());

            case TEXT:
                return categoryApi.text(tags, flags, query.older().orNull());

            default:
                // prepare the call to the official api. The call is only made on subscription.
                Observable<Api.Feed> officialCall = mainApi.itemsGet(promoted, following,
                        query.older().orNull(), query.newer().orNull(), query.around().orNull(),
                        flags, tags, likes, self, user);

                if (!query.around().isPresent() && !query.newer().isPresent()) {
                    if (tags != null && tags.startsWith("?")) {
                        // track the advanced search
                        Track.advancedSearch(tags.substring(1));

                        logger.info("Using general search api, but falling back on old one in case of an error.");
                        return categoryApi
                                .general(tags.substring(1), user, flags, query.older().orNull())
                                .onErrorResumeNext(officialCall);
                    }
                }

                return officialCall;
        }
    }

    public Observable<Api.Post> loadPostDetails(long id) {
        return mainApi.info(id);
    }

    @Value.Immutable
    public interface FeedQuery {
        FeedFilter feedFilter();

        Set<ContentType> contentTypes();

        Optional<Long> newer();

        Optional<Long> older();

        Optional<Long> around();
    }
}
