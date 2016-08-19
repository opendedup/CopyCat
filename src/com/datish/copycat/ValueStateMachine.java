package com.datish.copycat;

import io.atomix.copycat.server.Commit;
import io.atomix.copycat.server.StateMachine;
import io.atomix.copycat.server.StateMachineExecutor;

/**
 * Value state machine.
 *
 * @author <a href="http://github.com/kuujo>Jordan Halterman</a>
 */
public class ValueStateMachine extends StateMachine {
  private Commit<SetCommand> value;

  @Override
  protected void configure(StateMachineExecutor executor) {
    executor.register(SetCommand.class, this::set);
    executor.register(GetQuery.class, this::get);
    executor.register(DeleteCommand.class, this::delete);
  }

  /**
   * Sets the value.
   */
  private Object set(Commit<SetCommand> commit) {
    try {
    	System.out.println(commit.operation().value());
      Commit<SetCommand> previous = value;
      value = commit;
      if (previous != null) {
        Object result = previous.operation().value();
        previous.close();
        return result;
      }
      return null;
    } catch (Exception e) {
      commit.close();
      throw e;
    }
  }

  /**
   * Gets the value.
   */
  private Object get(Commit<GetQuery> commit) {
    try {
      return value != null ? value.operation().value() : null;
    } finally {
      commit.close();
    }
  }

  /**
   * Deletes the value.
   */
  private void delete(Commit<DeleteCommand> commit) {
    try {
      if (value != null) {
        value.close();
        value = null;
      }
    } finally {
      commit.close();
    }
  }

}
