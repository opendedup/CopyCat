package com.datish.copycat;

import io.atomix.copycat.Command;

/**
 * Value delete command.
 *
 * @author <a href="http://github.com/kuujo>Jordan Halterman</a>
 */
public class DeleteCommand implements Command<Void> {
  @Override
  public CompactionMode compaction() {
    return CompactionMode.TOMBSTONE;
  }
}
