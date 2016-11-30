package org.jenkinsci.plugins.workflow.steps;

import com.google.inject.AbstractModule;
import com.google.inject.MembersInjector;
import com.google.inject.ProvisionException;
import com.google.inject.TypeLiteral;
import com.google.inject.matcher.Matchers;
import com.google.inject.spi.TypeEncounter;
import com.google.inject.spi.TypeListener;

import javax.annotation.Nullable;
import java.io.IOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * @author Kohsuke Kawaguchi
 */
class ContextParameterModule extends AbstractModule {

    private static final Logger LOGGER = Logger.getLogger(ContextParameterModule.class.getName());

    private final Step step;
    private final StepContext context;

    ContextParameterModule(@Nullable Step step, StepContext context) {
        this.context = context;
        this.step = step;
    }

    @Override
    protected void configure() {
        bind(StepContext.class).toInstance(context);

        // make the outer 'this' object available at arbitrary super type of the actual concrete type
        // this will allow Step to subtype another Step and work as expected
        if (step!=null) {
            for (Class c=step.getClass(); c!=Step.class; c=c.getSuperclass()) {
                bind(c).toInstance(step);
            }
        } else {
            bindListener(Matchers.any(), new TypeListener() {
                @Override public <I> void hear(TypeLiteral<I> type, TypeEncounter<I> encounter) {
                    for (final Field f : type.getRawType().getDeclaredFields()) {
                        final Class<?> stepType = f.getType();
                        if (Step.class.isAssignableFrom(stepType)) {
                            encounter.register(new MembersInjector<I>() {
                                @Override public void injectMembers(I instance) {
                                    if (Modifier.isTransient(f.getModifiers())) {
                                        try {
                                            /*Constructor<?> stepCtor = */stepType.getConstructor();
                                            /* TODO does not work
                                            f.setAccessible(true);
                                            f.set(instance, stepCtor.newInstance());
                                            */
                                            LOGGER.log(Level.WARNING, "JENKINS-39134: {0} may not be marked @Inject; do not use AbstractStep'{',Execution,Descriptor'}'Impl", f);
                                        } catch (NoSuchMethodException x) {
                                            // fine, not a no-arg ctor, would not pose a problem anyway
                                        /*
                                        } catch (Exception x) {
                                            throw new ProvisionException("failed to work around JENKINS-39134 on " + f, x);
                                        */
                                        }
                                    }
                                }
                            });
                        }
                    }
                }
            });
        }

        bindListener(Matchers.any(), new TypeListener() {
            @Override
            public <I> void hear(TypeLiteral<I> type, TypeEncounter<I> encounter) {
                for (Field f : type.getRawType().getDeclaredFields()) {
                    if (f.isAnnotationPresent(StepContextParameter.class)) {
                        encounter.register(new FieldInjector<I>(f));
                    }
                }
                for (Method m : type.getRawType().getDeclaredMethods()) {
                    if (m.isAnnotationPresent(StepContextParameter.class)) {
                        encounter.register(new MethodInjector<I>(m));
                    }
                }
            }

            abstract class ParameterInjector<T> implements MembersInjector<T> {
                Object value(Class type) throws IOException, InterruptedException {
                    return context.get(type);
                }
            }

            class FieldInjector<T> extends ParameterInjector<T> {
                final Field f;

                FieldInjector(Field f) {
                    this.f = f;
                    f.setAccessible(true);
                }

                @Override
                public void injectMembers(T instance) {
                    try {
                        f.set(instance, context.get(f.getType()));
                    } catch (IllegalAccessException e) {
                        throw (Error) new IllegalAccessError(e.getMessage()).initCause(e);
                    } catch (InterruptedException e) {
                        throw new ProvisionException("Failed to set a context parameter", e);
                    } catch (IOException e) {
                        throw new ProvisionException("Failed to set a context parameter", e);
                    }
                }
            }

            class MethodInjector<T> extends ParameterInjector<T> {
                final Method m;

                MethodInjector(Method m) {
                    this.m = m;
                    m.setAccessible(true);
                }

                @Override
                public void injectMembers(T instance) {
                    try {
                        Class<?>[] types = m.getParameterTypes();
                        Object[] args = new Object[types.length];
                        for (int i = 0; i < args.length; i++) {
                            args[i] = context.get(types[i]);
                        }
                        m.invoke(instance, args);
                    } catch (IllegalAccessException e) {
                        throw (Error) new IllegalAccessError(e.getMessage()).initCause(e);
                    } catch (InvocationTargetException e) {
                        throw new ProvisionException("Failed to set a context parameter", e);
                    } catch (InterruptedException e) {
                        throw new ProvisionException("Failed to set a context parameter", e);
                    } catch (IOException e) {
                        throw new ProvisionException("Failed to set a context parameter", e);
                    }
                }
            }
        });
    }
}
