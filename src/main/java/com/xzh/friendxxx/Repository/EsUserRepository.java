package com.xzh.friendxxx.Repository;

import com.github.pagehelper.Page;
import com.xzh.friendxxx.model.entity.esentity.EsPost;
import com.xzh.friendxxx.model.entity.esentity.EsUser;
import org.springframework.data.domain.Pageable;
import org.springframework.data.elasticsearch.annotations.Query;
import org.springframework.data.elasticsearch.repository.ElasticsearchRepository;

//public interface EsUserRepository extends ElasticsearchRepository<EsUser, Long> {
//    @Query("{\"match\": {\"username.ngram\": \"?0\"}}")
//    Page<EsUser> findByUsernameContaining(String query, Pageable pageable);
//}
