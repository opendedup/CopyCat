package com.datish.copycat;

import io.atomix.copycat.Command;

/**
 * Value set command.
 *
 * @author <a href="http://github.com/kuujo>Jordan Halterman</a>
 */
public class SetCommand implements Command<Object> {
  private final Object value;

  public SetCommand(Object value) {
    this.value = value;
  }

  /**
   * Returns the value.
   */
  public Object value() {
    return value;
  }

  @Override
  public CompactionMode compaction() {
    return CompactionMode.QUORUM;
  }

}
