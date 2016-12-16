/**
 * Copyright (C) 2014 The OwlSim authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */
package org.monarchinitiative.owlsim.services.resources;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import javax.inject.Inject;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.MediaType;

import org.monarchinitiative.owlsim.compute.cpt.IncoherentStateException;
import org.monarchinitiative.owlsim.compute.matcher.NegationAwareProfileMatcher;
import org.monarchinitiative.owlsim.compute.matcher.ProfileMatcher;
import org.monarchinitiative.owlsim.kb.filter.AnonIndividualFilter;
import org.monarchinitiative.owlsim.kb.filter.TypeFilter;
import org.monarchinitiative.owlsim.kb.filter.UnknownFilterException;
import org.monarchinitiative.owlsim.model.match.MatchSet;
import org.monarchinitiative.owlsim.model.match.ProfileQuery;
import org.monarchinitiative.owlsim.model.match.ProfileQueryFactory;
import org.monarchinitiative.owlsim.services.exceptions.NonNegatedMatcherException;
import org.monarchinitiative.owlsim.services.exceptions.UnknownMatcherException;
import org.prefixcommons.CurieUtil;

import com.codahale.metrics.annotation.Timed;
import com.wordnik.swagger.annotations.Api;
import com.wordnik.swagger.annotations.ApiOperation;
import com.wordnik.swagger.annotations.ApiParam;

import io.dropwizard.jersey.caching.CacheControl;

@Path("/match")
@Api(value = "/match", description = "match services")
@Produces({MediaType.APPLICATION_JSON})
public class MatchResource {

  // TODO: this needs to be updated for Dropwizard 1.0, use HK2
  @Inject
  Map<String, ProfileMatcher> matchers = new HashMap<>();

  @Inject
  CurieUtil curieUtil;

  @GET
  @Path("/matchers")
  @ApiOperation(value = "Get registered profile matchers", response = Collection.class,
      notes = "Additional notes on the matchers resource.")
  public Collection<String> getMatchers() {
    return matchers.keySet();
  }

  @GET
  @Path("/{matcher}")
  @Timed
  @CacheControl(maxAge = 2, maxAgeUnit = TimeUnit.HOURS)
  @ApiOperation(value = "Match", response = MatchSet.class,
      notes = "Additional notes on the match resource.")
  public MatchSet getMatches(
      @ApiParam(value = "The name of the matcher to use",
          required = true) @PathParam("matcher") String matcherName,
      @ApiParam(value = "Class IDs to be matched",
          required = false) @QueryParam("id") Set<String> ids,
      @ApiParam(value = "Negated Class IDs",
          required = false) @QueryParam("negatedId") Set<String> negatedIds,
      @ApiParam(value = "Target Class IDs",
          required = false) @QueryParam("targetClassId") Set<String> targetClassIds,
      @ApiParam(value = "Filter individuals by type",
          required = false) @QueryParam("filterClassId") String filterId,
      @ApiParam(value = "cutoff limit", required = false) @QueryParam("limit") Integer limit)
      throws UnknownFilterException, IncoherentStateException {
    if (!matchers.containsKey(matcherName)) {
      throw new UnknownMatcherException(matcherName);
    }
    ProfileMatcher matcher = matchers.get(matcherName);

    Set<String> resolvedIds =
        ids.stream().map(id -> curieUtil.getIri(id).or(id)).collect(Collectors.toSet());
    Set<String> resolvedNegatedIds =
        negatedIds.stream().map(id -> curieUtil.getIri(id).or(id)).collect(Collectors.toSet());
    Set<String> resolvedTargetClassIds =
        targetClassIds.stream().map(id -> curieUtil.getIri(id).or(id)).collect(Collectors.toSet());

    // Verify that matcher is negation aware if negated IDs are used
    if (!resolvedNegatedIds.isEmpty()
        && !NegationAwareProfileMatcher.class.isAssignableFrom(matcher.getClass())) {
      throw new NonNegatedMatcherException(matcherName);
    }
    ProfileQuery query =
        ProfileQueryFactory.createQueryWithNegation(resolvedIds, resolvedNegatedIds);

    if (limit != null)
      query.setLimit(limit);
    if (filterId != null) {
      TypeFilter filter = new TypeFilter(filterId, false, false);
      query.setFilter(filter);
    }
    if (!resolvedTargetClassIds.isEmpty()) {
      ProfileQuery targetPQ = ProfileQueryFactory.createQuery(resolvedTargetClassIds);
      AnonIndividualFilter filter = new AnonIndividualFilter(targetPQ);
      query.setFilter(filter);
    }
    MatchSet matchSet = matcher.findMatchProfile(query);

    // TODO have something more generic
    // replacing with CURIEs when found
    ProfileQuery pq = matchSet.getQuery();
    Set<String> classIdsCurie = iriToCurie(pq.getQueryClassIds());
    Set<String> individualIdsCurie = iriToCurie(pq.getReferenceIndividualIds());
    pq.setQueryClassIds(classIdsCurie);
    pq.setReferenceIndividualIds(individualIdsCurie);
    matchSet.setQuery(pq);

    matchSet.setMatches(matchSet.getMatches().stream().map(match -> {
      String curie = curieUtil.getCurie(match.getMatchId()).or(match.getMatchId());
      match.setMatchId(curie);
      return match;
    }).collect(Collectors.toList()));

    return matchSet;
  }

  private Set<String> iriToCurie(Set<String> s) {
    if (s == null) {
      return null;
    }
    return s.stream().map(iri -> curieUtil.getCurie(iri).or(iri)).collect(Collectors.toSet());
  }

  // TODO - API for comparing two entities

}
