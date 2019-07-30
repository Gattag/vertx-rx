/*
 * Copyright (c) 2011-2018 Contributors to the Eclipse Foundation
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0, or the Apache License, Version 2.0
 * which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */

package io.vertx.rxjava.ext.sql;

import io.vertx.rxjava.ext.sql.impl.InTransactionCompletable;
import io.vertx.rxjava.ext.sql.impl.InTransactionObservable;
import io.vertx.rxjava.ext.sql.impl.InTransactionSingle;
import rx.Completable;
import rx.Observable;
import rx.Single;
import rx.exceptions.Exceptions;

import java.util.function.Function;

/**
 * Utilities for generating observables with a {@link SQLClient}.
 *
 * @author Thomas Segismont
 */
public class SQLClientHelper {

  /**
   * Creates a {@link Observable.Transformer} decorating an {@link Observable} with transaction management for a given {@link SQLConnection}.
   * <p>
   * If the upstream {@link Observable} completes (<em>onComplete</em>), the transaction is committed.
   * If the upstream {@link Observable} emits an error (<em>onError</em>), the transaction is rollbacked.
   * <p>
   * Eventually, the given {@link SQLConnection} is put back in <em>autocommit</em> mode.
   *
   * @param sqlConnection the {@link SQLConnection} used for database operations and transaction management
   * @param <T> the type of the items emitted by the upstream {@link Observable}
   * @return a {@link Observable.Transformer} decorating an {@link Observable} with transaction management
   */
  public static <T> Observable.Transformer<T, T> txObservableTransformer(SQLConnection sqlConnection) {
    return new InTransactionObservable<>(sqlConnection);
  }

  /**
   * Generates a {@link Observable} from {@link SQLConnection} operations executed inside a transaction.
   *
   * @param client the {@link SQLClient}
   * @param sourceSupplier a user-provided function returning a {@link Observable} generated by interacting with the given {@link SQLConnection}
   * @param <T> the type of the items emitted by the {@link Observable}
   * @return an {@link Observable} generated from {@link SQLConnection} operations executed inside a transaction
   */
  public static <T> Observable<T> inTransactionObservable(SQLClient client, Function<SQLConnection, Observable<T>> sourceSupplier) {
    return usingConnectionObservable(client, conn -> sourceSupplier.apply(conn).compose(txObservableTransformer(conn)));
  }

  /**
   * Creates a {@link Single.Transformer} decorating a {@link Single} with transaction management for a given {@link SQLConnection}.
   * <p>
   * If the upstream {@link Single} emits a value (<em>onSuccess</em>), the transaction is committed.
   * If the upstream {@link Single} emits an error (<em>onError</em>), the transaction is rollbacked.
   * <p>
   * Eventually, the given {@link SQLConnection} is put back in <em>autocommit</em> mode.
   *
   * @param sqlConnection the {@link SQLConnection} used for database operations and transaction management
   * @param <T> the type of the item emitted by the upstream {@link Single}
   * @return a {@link Single.Transformer} decorating a {@link Single} with transaction management
   */
  public static <T> Single.Transformer<T, T> txSingleTransformer(SQLConnection sqlConnection) {
    return new InTransactionSingle<>(sqlConnection);
  }

  /**
   * Generates a {@link Single} from {@link SQLConnection} operations executed inside a transaction.
   *
   * @param client the {@link SQLClient}
   * @param sourceSupplier a user-provided function returning a {@link Single} generated by interacting with the given {@link SQLConnection}
   * @param <T> the type of the item emitted by the {@link Single}
   * @return a {@link Single} generated from {@link SQLConnection} operations executed inside a transaction
   */
  public static <T> Single<T> inTransactionSingle(SQLClient client, Function<SQLConnection, Single<T>> sourceSupplier) {
    return usingConnectionSingle(client, conn -> sourceSupplier.apply(conn).compose(txSingleTransformer(conn)));
  }

  /**
   * Creates a {@link Completable.Transformer} decorating a {@link Completable} with transaction management for a given {@link SQLConnection}.
   * <p>
   * If the upstream {@link Completable} completes (<em>onComplete</em>), the transaction is committed.
   * If the upstream {@link Completable} emits an error (<em>onError</em>), the transaction is rollbacked.
   * <p>
   * Eventually, the given {@link SQLConnection} is put back in <em>autocommit</em> mode.
   *
   * @param sqlConnection the {@link SQLConnection} used for database operations and transaction management
   * @return a {@link Completable.Transformer} decorating a {@link Completable} with transaction management
   */
  public static Completable.Transformer txCompletableTransformer(SQLConnection sqlConnection) {
    return new InTransactionCompletable(sqlConnection);
  }

  /**
   * Generates a {@link Completable} from {@link SQLConnection} operations executed inside a transaction.
   *
   * @param client the {@link SQLClient}
   * @param sourceSupplier a user-provided function returning a {@link Completable} generated by interacting with the given {@link SQLConnection}
   * @return a {@link Completable} generated from {@link SQLConnection} operations executed inside a transaction
   */
  public static Completable inTransactionCompletable(SQLClient client, Function<SQLConnection, Completable> sourceSupplier) {
    return usingConnectionCompletable(client, conn -> sourceSupplier.apply(conn).compose(txCompletableTransformer(conn)));
  }

  /**
   * Generates a {@link Observable} from {@link SQLConnection} operations.
   *
   * @param client the {@link SQLClient}
   * @param sourceSupplier a user-provided function returning a {@link Observable} generated by interacting with the given {@link SQLConnection}
   * @param <T> the type of the items emitted by the {@link Observable}
   * @return an {@link Observable} generated from {@link SQLConnection} operations
   */
  public static <T> Observable<T> usingConnectionObservable(SQLClient client, Function<SQLConnection, Observable<T>> sourceSupplier) {
    return client.rxGetConnection().flatMapObservable(conn -> {
      try {
        return sourceSupplier.apply(conn).doAfterTerminate(conn::close);
      } catch (Throwable t) {
        Exceptions.throwIfFatal(t);
        conn.close();
        return Observable.error(t);
      }
    });
  }

  /**
   * Generates a {@link Single} from {@link SQLConnection} operations.
   *
   * @param client the {@link SQLClient}
   * @param sourceSupplier a user-provided function returning a {@link Single} generated by interacting with the given {@link SQLConnection}
   * @param <T> the type of the item emitted by the {@link Single}
   * @return a {@link Single} generated from {@link SQLConnection} operations
   */
  public static <T> Single<T> usingConnectionSingle(SQLClient client, Function<SQLConnection, Single<T>> sourceSupplier) {
    return client.rxGetConnection().flatMap(conn -> {
      try {
        return sourceSupplier.apply(conn).doAfterTerminate(conn::close);
      } catch (Throwable t) {
        Exceptions.throwIfFatal(t);
        conn.close();
        return Single.error(t);
      }
    });
  }

  /**
   * Generates a {@link Completable} from {@link SQLConnection} operations.
   *
   * @param client the {@link SQLClient}
   * @param sourceSupplier a user-provided function returning a {@link Completable} generated by interacting with the given {@link SQLConnection}
   * @return a {@link Completable} generated from {@link SQLConnection} operations
   */
  public static Completable usingConnectionCompletable(SQLClient client, Function<SQLConnection, Completable> sourceSupplier) {
    return client.rxGetConnection().flatMapCompletable(conn -> {
      try {
        return sourceSupplier.apply(conn).doAfterTerminate(conn::close);
      } catch (Throwable t) {
        Exceptions.throwIfFatal(t);
        conn.close();
        return Completable.error(t);
      }
    });
  }

  private SQLClientHelper() {
    // Utility
  }
}
