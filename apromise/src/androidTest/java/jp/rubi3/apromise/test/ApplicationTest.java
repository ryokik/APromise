package jp.rubi3.apromise.test;

import android.app.Application;
import android.os.Handler;
import android.os.Looper;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.test.ApplicationTestCase;

import java.util.List;
import java.util.concurrent.CountDownLatch;

import jp.rubi3.apromise.Callback;
import jp.rubi3.apromise.Filter;
import jp.rubi3.apromise.Function;
import jp.rubi3.apromise.Pipe;
import jp.rubi3.apromise.Promise;
import jp.rubi3.apromise.Resolver;

/**
 * <a href="http://d.android.com/tools/testing/testing_android.html">Testing Fundamentals</a>
 */
public class ApplicationTest extends ApplicationTestCase<Application> {
    public ApplicationTest() {
        super(Application.class);
    }

    public void testCallback() throws Exception {
        final StringBuilder builder = new StringBuilder();
        final CountDownLatch latch = new CountDownLatch(1);

        new Handler(Looper.getMainLooper()).post(new Runnable() {
            @Override
            public void run() {
                Promise.resolve(
                        builder.append("A")
                ).onThen(new Callback<StringBuilder>() {
                    @Override
                    public void callback(StringBuilder result) throws Exception {
                        result.append("B");
                        throw new Exception("C"); // throw on then callback
                    }
                }).onThen(new Callback<StringBuilder>() {
                    @Override
                    public void callback(StringBuilder result) throws Exception {
                        result.append("X"); // through
                    }
                }).onCatch(new Callback<Exception>() {
                    @Override
                    public void callback(Exception result) throws Exception {
                        builder.append(result.getMessage());
                    }
                }).onFinally(new Callback<Promise<StringBuilder>>() {
                    @Override
                    public void callback(Promise<StringBuilder> result) throws Exception {
                        if (result.isRejected()) {
                            builder.append("D"); // through result object
                        } else {
                            builder.append("X");
                        }
                        latch.countDown();
                    }
                });
            }
        });

        latch.await();
        assertEquals("ABCD", builder.toString());
    }

    public void testFilter() throws Exception {
        final StringBuilder builder = new StringBuilder();
        final CountDownLatch latch = new CountDownLatch(1);

        new Handler(Looper.getMainLooper()).post(new Runnable() {
            @Override
            public void run() {
                Promise.resolve(
                        builder.append("A")
                ).onThen(new Filter<StringBuilder, String>() {
                    @Override
                    public String filter(StringBuilder result) throws Exception {
                        builder.append("B");
                        return "C";
                    }
                }).onThen(new Filter<String, Exception>() {
                    @Override
                    public Exception filter(String result) throws Exception {
                        builder.append(result);
                        return new Exception("D"); // filter returns Exception to reject
                    }
                }).onThen(new Filter<Exception, String>() {
                    @Nullable
                    @Override
                    public String filter(Exception result) throws Exception {
                        return "X"; // through
                    }
                }).onCatch(new Filter<Exception, String>() {
                    @Override
                    public String filter(Exception result) throws Exception {
                        builder.append(result.getMessage());
                        return "E";
                    }
                }).onFinally(new Filter<Promise<String>, String>() {
                    @Nullable
                    @Override
                    public String filter(Promise<String> result) throws Exception {
                        builder.append(result.getResult());
                        return "F";
                    }
                }).onFinally(new Filter<Promise<String>, Void>() {
                    @Nullable
                    @Override
                    public Void filter(Promise<String> result) throws Exception {
                        builder.append(result.getResult());
                        latch.countDown();
                        return null;
                    }
                });
            }
        });

        latch.await();
        assertEquals("ABCDEF", builder.toString());
    }

    public void testPipe() throws Exception {
        final StringBuilder builder = new StringBuilder();
        final CountDownLatch latch = new CountDownLatch(1);

        Handler handler = new Handler(Looper.getMainLooper());
        handler.post(new Runnable() {
            @Override
            public void run() {
                Promise.resolve(
                        "A"
                ).onThen(new Pipe<String, String>() {
                    @NonNull
                    @Override
                    public Promise<String> pipe(String result) throws Exception {
                        builder.append(result);
                        return Promise.resolve("B");
                    }
                }).onThen(new Pipe<String, String>() {
                    @NonNull
                    @Override
                    public Promise<String> pipe(String result) throws Exception {
                        builder.append(result);
                        throw new Exception("C");
                    }
                }).onThen(new Pipe<String, Character>() {
                    @NonNull
                    @Override
                    public Promise<Character> pipe(String result) throws Exception {
                        builder.append("X");
                        return null; // cause nullpo
                    }
                }).onCatch(new Pipe<Exception, Character>() {
                    @NonNull
                    @Override
                    public Promise<Character> pipe(Exception result) throws Exception {
                        builder.append(result.getMessage());
                        return Promise.reject(new Exception("D"));
                    }
                }).onCatch(new Pipe<Exception, Character>() {
                    @NonNull
                    @Override
                    public Promise<Character> pipe(Exception result) throws Exception {
                        builder.append(result.getMessage());
                        return Promise.resolve('E');
                    }
                }).onFinally(new Pipe<Promise<Character>, CountDownLatch>() {
                    @Nullable
                    @Override
                    public Promise<CountDownLatch> pipe(Promise<Character> result) throws Exception {
                        builder.append(result.getResult());
                        latch.countDown();
                        return Promise.resolve(latch);
                    }
                });
            }
        });

        latch.await();
        assertEquals("ABCDE", builder.toString());
    }

    public void testResolved() throws Exception {
        assertTrue(Promise.resolve(null).isFulfilled());
        assertTrue(Promise.reject(null).isRejected());
    }

    public void testStatus() throws Exception {
        Handler handler = new Handler(Looper.getMainLooper());

        Promise pending = new Promise(handler, new Function() {
            @Override
            public void function(Resolver resolver) throws Exception {
            }
        });
        assertTrue(pending.isPending());
        assertFalse(pending.isFulfilled());
        assertFalse(pending.isRejected());

        Promise fulfilled = Promise.resolve(null);
        assertFalse(fulfilled.isPending());
        assertTrue(fulfilled.isFulfilled());
        assertFalse(fulfilled.isRejected());

        Promise rejected = Promise.reject(null);
        assertFalse(rejected.isPending());
        assertFalse(rejected.isFulfilled());
        assertTrue(rejected.isRejected());
    }

    public void testMultipleChain() throws Exception {
        final StringBuilder builder = new StringBuilder();
        final CountDownLatch latch = new CountDownLatch(1);

        final Handler handler = new Handler(Looper.getMainLooper());
        handler.post(new Runnable() {
            @Override
            public void run() {
                Promise resolved = Promise.resolve(null);
                resolved.onThen(new Callback() {
                    @Override
                    public void callback(Object result) throws Exception {
                        builder.append("A");
                    }
                });
                resolved.onThen(new Callback() {
                    @Override
                    public void callback(Object result) throws Exception {
                        builder.append("B");
                    }
                });

                final Promise<Character> pending = new Promise<>(new Function<Character>() {
                    @Override
                    public void function(final Resolver<Character> resolver) throws Exception {
                        handler.postDelayed(new Runnable() {
                            @Override
                            public void run() {
                                resolver.fulfill('D');
                            }
                        }, 1000);
                    }
                });
                pending.onThen(new Callback<Character>() {
                    @Override
                    public void callback(Character result) throws Exception {
                        builder.append("C");
                    }
                });
                pending.onThen(new Callback<Character>() {
                    @Override
                    public void callback(Character result) throws Exception {
                        builder.append(result);
                        latch.countDown();
                    }
                });
            }
        });

        latch.await();

        assertEquals("ABCD", builder.toString());
    }

    public void testFunction() throws Exception {
        final StringBuilder builder = new StringBuilder();
        final CountDownLatch latch = new CountDownLatch(1);

        final Handler handler = new Handler(Looper.getMainLooper());
        new Promise<>(handler, new Function<String>() {
            @Override
            public void function(Resolver<String> resolver) throws Exception {
                resolver.fulfill(null);
                resolver.fulfill("helloo"); // nop
                builder.append("A");
            }
        }).onCatch(new Callback<Exception>() {
            @Override
            public void callback(Exception result) throws Exception {
                builder.append("X");
            }
        }).onFinally(new Callback<Promise<String>>() {
            @Override
            public void callback(Promise<String> result) throws Exception {
                latch.countDown();
            }
        });

        latch.await();

        assertEquals("A", builder.toString());
    }

    public void testAllDone() throws Exception {
        final StringBuilder builder = new StringBuilder();
        final CountDownLatch latch = new CountDownLatch(1);

        final Handler handler = new Handler(Looper.getMainLooper());

        handler.post(new Runnable() {
            @Override
            public void run() {
                Promise.all(
                        Promise.resolve("A"),
                        Promise.resolve("B"),
                        new Promise<>(new Function<String>() {
                            @Override
                            public void function(final Resolver<String> resolver) throws Exception {
                                handler.postDelayed(new Runnable() {
                                    @Override
                                    public void run() {
                                        resolver.fulfill("C");
                                    }
                                }, 1000);
                            }
                        })
                ).onThen(new Callback<List<String>>() {
                    @Override
                    public void callback(List<String> result) throws Exception {
                        for (String one : result) {
                            builder.append(one);
                        }
                        latch.countDown();
                    }
                });
            }
        });

        latch.await();
        assertEquals("ABC", builder.toString());
    }

    public void testAllOneFail() throws Exception {
        final StringBuilder builder = new StringBuilder();
        final CountDownLatch latch = new CountDownLatch(1);

        final Handler handler = new Handler(Looper.getMainLooper());

        handler.post(new Runnable() {
            @Override
            public void run() {
                Promise.all(
                        Promise.resolve("X"),
                        Promise.resolve("X"),
                        new Promise<>(new Function<String>() {
                            @Override
                            public void function(final Resolver<String> resolver) throws Exception {
                                handler.postDelayed(new Runnable() {
                                    @Override
                                    public void run() {
                                        resolver.reject(new RuntimeException("ABC"));
                                    }
                                }, 1000);
                            }
                        })
                ).onCatch(new Callback<Exception>() {
                    @Override
                    public void callback(Exception result) throws Exception {
                        builder.append(result.getMessage());

                    }
                }).onFinally(new Callback<Promise<List<String>>>() {
                    @Override
                    public void callback(Promise<List<String>> result) throws Exception {
                        latch.countDown();
                    }
                });
            }
        });

        latch.await();
        assertEquals("ABC", builder.toString());
    }

    public void testDoneFilterFailFilter() throws Exception {
        final StringBuilder builder = new StringBuilder();
        final CountDownLatch latch = new CountDownLatch(1);

        final Handler handler = new Handler(Looper.getMainLooper());

        handler.post(new Runnable() {
            @Override
            public void run() {
                Promise.all(
                        Promise.resolve("X"),
                        Promise.resolve("X"),
                        new Promise<>(new Function<String>() {
                            @Override
                            public void function(final Resolver<String> resolver) throws Exception {
                                handler.postDelayed(new Runnable() {
                                    @Override
                                    public void run() {
                                        resolver.reject(new RuntimeException("ABC"));
                                    }
                                }, 1000);
                            }
                        })
                ).onCatch(new Callback<Exception>() {
                    @Override
                    public void callback(Exception result) throws Exception {
                        builder.append(result.getMessage());

                    }
                }).onFinally(new Callback<Promise<List<String>>>() {
                    @Override
                    public void callback(Promise<List<String>> result) throws Exception {
                        latch.countDown();
                    }
                });
            }
        });

        latch.await();
        assertEquals("ABC", builder.toString());
    }


    public void testResult() throws Exception {
        String result = "testResult";
        Promise promise = Promise.resolve(result);
        assertEquals(result, promise.getResult());

        promise = Promise.reject(null);
        assertNotNull(promise.getException());
    }
}