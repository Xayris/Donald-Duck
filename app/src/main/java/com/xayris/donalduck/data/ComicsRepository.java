package com.xayris.donalduck.data;

import android.content.Context;

import androidx.annotation.NonNull;

import com.xayris.donalduck.data.entities.Comic;
import com.xayris.donalduck.data.entities.Story;
import com.xayris.donalduck.ui.archive.ArchiveFragment;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

import io.realm.OrderedCollectionChangeSet;
import io.realm.OrderedRealmCollectionChangeListener;
import io.realm.Realm;
import io.realm.RealmConfiguration;
import io.realm.RealmList;
import io.realm.RealmResults;
import io.realm.Sort;

public class ComicsRepository implements OrderedRealmCollectionChangeListener<RealmResults<Comic>> {
    private static final String DB_NAME = "comics.db";

    private static ComicsRepository _singleton;

    public static ComicsRepository getInstance() {
        if(_singleton == null)
            _singleton = new ComicsRepository();
        return _singleton;
    }

    private RealmResults<Comic> _comics;
    private List<Comic> _homeComics;
    private ComicsArchiveResult _archiveComics;


    public void createDatabase(Context context) {
        Realm.init(context);
        RealmConfiguration config = new RealmConfiguration.Builder()
                .name(DB_NAME)
                .schemaVersion(1)
                .allowQueriesOnUiThread(true)
                .deleteRealmIfMigrationNeeded()
                .build();
        Realm.setDefaultConfiguration(config);
    }

    public Comic getComic(String issue)
    {
        Realm realm = Realm.getDefaultInstance();
        return realm.where(Comic.class).equalTo("issue", issue).findFirst();
    }

    public void saveComic(Comic comic) {
        Realm realm = Realm.getDefaultInstance();
        realm.beginTransaction();
        // checks existing stories
        RealmList<Story> stories = comic.getStories();
        for (Story story :
                stories) {
            // if the story already exists, it keeps the read flag value
            Story existingStory = realm.where(Story.class).equalTo("code", story.getCode()).findFirst();
            if (existingStory != null)
                story.setIsRead(existingStory.getIsRead());
        }
        comic.setStories(stories);
        realm.copyToRealmOrUpdate(comic);
        realm.commitTransaction();
    }


    public void processHomeComics() {
        Realm realm = Realm.getDefaultInstance();
        _homeComics = realm.copyFromRealm(_comics);
        _homeComics = _homeComics.stream().filter(comic -> comic.getReadStoriesCount() > 0 && comic.getReadStoriesCount() < comic.getStoriesCount()).collect(Collectors.toList());
        _homeComics.sort((o1, o2) -> {
            int o1Remaining = o1.getStoriesCount() - o1.getReadStoriesCount();
            int o2Remaining = o2.getStoriesCount() - o2.getReadStoriesCount();
            double o1Value = (float) o1Remaining / o1.getStoriesCount();
            double o2Value = (float) o2Remaining / o2.getStoriesCount();
            return Double.compare(o1Value, o2Value);
        });
    }

    public void loadComics() {
        if(_homeComics != null && _archiveComics != null)
            return;
        Realm realm = Realm.getDefaultInstance();
        _comics = realm.where(Comic.class).sort("issue", Sort.DESCENDING).findAll();
        _comics.addChangeListener(this);
        if(_homeComics == null)
            processHomeComics();
        if(_archiveComics == null)
            processComicsArchive();
    }

    public ComicsArchiveResult getArchive() {
        return _archiveComics;
    }

    public List<Comic> getHome() {
        return _homeComics;
    }

    private void processComicsArchive() {
        List<Comic> startedComics = new ArrayList<>();
        List<Comic> unstartedComics = new ArrayList<>();
        List<Comic> completedComics = new ArrayList<>();

        for (Comic c : _comics) {
            if(c.getReadStoriesCount() > 0 && c.getReadStoriesCount() < c.getStoriesCount())
                startedComics.add(c);
            else if (c.getReadStoriesCount() == 0)
                unstartedComics.add(c);
            else if (c.getReadStoriesCount() == c.getStoriesCount())
                completedComics.add(c);
        }
        _archiveComics = new ComicsArchiveResult(startedComics, unstartedComics, completedComics);
    }

    @Override
    public void onChange(@NonNull RealmResults<Comic> comics, @NonNull OrderedCollectionChangeSet changeSet) {
        processComicsArchive();
        processHomeComics();
    }

    public List<Comic> getComicsByArchiveType(ArchiveFragment.ArchiveType type) {
        switch (type) {
            case Unstarted:
                return  getUnstartedComics();
            case Completed:
                return getCompletedComics();
            case All:
                return getAllComics();
        }
        return null;
    }


    public List<Comic> getStartedComics() {
        return getArchive().getStartedComics();
    }

    public List<Comic> getUnstartedComics() {
        return getArchive().getUnstartedComics();
    }

    public List<Comic> getCompletedComics() {
        return getArchive().getCompletedComics();
    }

    public List<Comic> getAllComics() {
        List<Comic> comics = new ArrayList<>();
        comics.addAll(getStartedComics());
        comics.addAll(getUnstartedComics());
        comics.addAll(getCompletedComics());

        comics.sort((o1, o2) -> o2.getIssue().compareTo(o1.getIssue()));
        return comics;
    }

    public void setStoryRead(Story story) {
        Realm realm = Realm.getDefaultInstance();
        realm.beginTransaction();
        story.setIsRead(!story.getIsRead());
        realm.copyToRealmOrUpdate(story);
        realm.commitTransaction();
    }

    public void deleteComic(Comic comic) {
        Realm realm = Realm.getDefaultInstance();
        realm.beginTransaction();
        Objects.requireNonNull(realm.where(Comic.class).equalTo("issue", comic.getIssue()).findFirst()).deleteFromRealm();
        realm.commitTransaction();
    }

    public void updateComicCoverUrl(Comic comic, String coverUrl) {
        Realm realm = Realm.getDefaultInstance();
        realm.beginTransaction();
        comic.setCoverUrl(coverUrl);
        realm.copyToRealmOrUpdate(comic);
        realm.commitTransaction();
    }

    public static class ComicsArchiveResult {
        private final List<Comic> _startedComics;
        private final List<Comic> _unstartedComics;
        private final List<Comic> _completedComics;

        public ComicsArchiveResult(List<Comic> startedComics, List<Comic> unstartedComics, List<Comic> completedComics)
        {
            _startedComics = startedComics;
            _unstartedComics = unstartedComics;
            _completedComics = completedComics;
        }

        public List<Comic> getStartedComics() {
            return _startedComics;
        }

        public List<Comic> getUnstartedComics() {
            return _unstartedComics;
        }

        public List<Comic> getCompletedComics() {
            return _completedComics;
        }
    }

}