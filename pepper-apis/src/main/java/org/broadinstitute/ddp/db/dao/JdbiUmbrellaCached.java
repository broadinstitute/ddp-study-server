package org.broadinstitute.ddp.db.dao;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;
import javax.cache.Cache;
import javax.cache.expiry.Duration;

import org.broadinstitute.ddp.cache.CacheService;
import org.broadinstitute.ddp.cache.ModelChangeType;
import org.broadinstitute.ddp.db.dto.UmbrellaDto;
import org.jdbi.v3.core.Handle;

public class JdbiUmbrellaCached extends SQLObjectWrapper<JdbiUmbrella> implements JdbiUmbrella {
    private static Cache<Long, UmbrellaDto> idToUmbrellaCache;

    public JdbiUmbrellaCached(Handle handle) {
        super(handle, JdbiUmbrella.class);
        initializeCache();
    }

    private void initializeCache() {
        if (idToUmbrellaCache == null) {
            // eternal!
            idToUmbrellaCache = CacheService.getInstance().getOrCreateCache("idToUmbrellaCache",
                    new Duration(),
                    ModelChangeType.UMBRELLA,
                    this);
        }
    }

    private boolean isUsingNullCache() {
        return isNullCache(idToUmbrellaCache);
    }

    private void cacheAllIfNeeded() {
        if (!idToUmbrellaCache.iterator().hasNext() && !isUsingNullCache()) {
            delegate.findAll().stream().forEach(umbrella -> idToUmbrellaCache.put(umbrella.getId(), umbrella));
        }
    }

    public Stream<UmbrellaDto> streamAll() {
        cacheAllIfNeeded();
        return StreamSupport.stream(idToUmbrellaCache.spliterator(), false)
                .map(entry -> entry.getValue());
    }

    public List<UmbrellaDto> findAll() {
        if (isUsingNullCache()) {
            return delegate.findAll();
        } else {
            return streamAll().collect(Collectors.toList());
        }
    }

    @Override
    public Optional<Long> findIdByName(String umbrellaName) {
        if (isUsingNullCache()) {
            return delegate.findIdByName(umbrellaName);
        } else {
            return streamAll().filter(umbrella -> umbrella.getName().equals(umbrellaName)).findAny().map(dto -> dto.getId());
        }
    }

    @Override
    public Optional<UmbrellaDto> findByGuid(String umbrellaGuid) {
        if (isUsingNullCache()) {
            return delegate.findByGuid(umbrellaGuid);
        } else {
            return streamAll().filter(umbrella -> umbrella.getGuid().equals(umbrellaGuid)).findAny();
        }
    }

    @Override
    public Optional<UmbrellaDto> findById(long umbrellaId) {
        if (isUsingNullCache()) {
            return delegate.findById(umbrellaId);
        } else {
            cacheAllIfNeeded();
            return Optional.ofNullable(idToUmbrellaCache.get(umbrellaId));
        }
    }

    @Override
    public long insert(String umbrellaName, String umbrellaGuid) {
        long id = delegate.insert(umbrellaName, umbrellaGuid);
        notifyModelUpdated(ModelChangeType.UMBRELLA, id);
        return id;
    }

    @Override
    public int deleteById(long id) {
        int val = delegate.deleteById(id);
        notifyModelUpdated(ModelChangeType.UMBRELLA, id);
        return val;
    }
}
