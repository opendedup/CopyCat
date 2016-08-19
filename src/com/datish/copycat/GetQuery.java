package com.datish.copycat;

import io.atomix.copycat.Query;

/**
 * Value get query.
 *
 * @author <a href="http://github.com/kuujo>Jordan Halterman</a>
 */
public class GetQuery implements Query<Object> {

  @Override
  public ConsistencyLevel consistency() {
    return ConsistencyLevel.LINEARIZABLE_LEASE;
  }

}
