/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache license, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the license for the specific language governing permissions and
 * limitations under the license.
 */
package org.apache.logging.log4j.message;

import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Collection;
import java.util.Date;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.apache.logging.log4j.util.StringBuilderFormattable;

/**
 * Handles messages that consist of a format string containing '{}' to represent each replaceable token, and
 * the parameters.
 * <p>
 * This class was originally written for <a href="http://lilithapp.com/">Lilith</a> by Joern Huxhorn where it is
 * licensed under the LGPL. It has been relicensed here with his permission providing that this attribution remain.
 * </p>
 */
public class ParameterizedMessage implements ReusableMessage {

    /**
     * Prefix for recursion.
     */
    public static final String RECURSION_PREFIX = "[...";
    /**
     * Suffix for recursion.
     */
    public static final String RECURSION_SUFFIX = "...]";

    /**
     * Prefix for errors.
     */
    public static final String ERROR_PREFIX = "[!!!";
    /**
     * Separator for errors.
     */
    public static final String ERROR_SEPARATOR = "=>";
    /**
     * Separator for error messages.
     */
    public static final String ERROR_MSG_SEPARATOR = ":";
    /**
     * Suffix for errors.
     */
    public static final String ERROR_SUFFIX = "!!!]";

    private static final long serialVersionUID = -665975803997290697L;

    private static final int HASHVAL = 31;

    private static final char DELIM_START = '{';
    private static final char DELIM_STOP = '}';
    private static final char ESCAPE_CHAR = '\\';

    // storing JDK classes in ThreadLocals does not cause memory leaks in web apps, so this is okay
    private static ThreadLocal<StringBuilder> threadLocalStringBuilder = new ThreadLocal<>();
    private static ThreadLocal<SimpleDateFormat> threadLocalSimpleDateFormat = new ThreadLocal<>();
    private static ThreadLocal<Object[]> threadLocalUnrolledArgs = new ThreadLocal<>();

    private String messagePattern;
    private int argCount;
    private transient Object[] argArray;

    private boolean isThreadLocalMessageInitialized;
    private transient Throwable throwable;
    private boolean reused;

    /**
     * Creates a parameterized message.
     * @param messagePattern The message "format" string. This will be a String containing "{}" placeholders
     * where parameters should be substituted.
     * @param arguments The arguments for substitution.
     * @param throwable A Throwable.
     * @deprecated Use constructor ParameterizedMessage(String, Object[], Throwable) instead
     */
    public ParameterizedMessage(final String messagePattern, final String[] arguments, final Throwable throwable) {
        this.argArray = arguments;
        this.throwable = throwable;
        init(messagePattern, arguments == null ? 0 : arguments.length);
    }

    /**
     * Creates a parameterized message.
     * @param messagePattern The message "format" string. This will be a String containing "{}" placeholders
     * where parameters should be substituted.
     * @param arguments The arguments for substitution.
     * @param throwable A Throwable.
     */
    public ParameterizedMessage(final String messagePattern, final Object[] arguments, final Throwable throwable) {
        this.argArray = arguments;
        this.throwable = throwable;
        init(messagePattern, arguments == null ? 0 : arguments.length);
    }

    /**
     * Constructs a ParameterizedMessage which contains the arguments converted to String as well as an optional
     * Throwable.
     *
     * <p>If the last argument is a Throwable and is NOT used up by a placeholder in the message pattern it is returned
     * in {@link #getThrowable()} and won't be contained in the created String[].
     * If it is used up {@link #getThrowable()} will return null even if the last argument was a Throwable!</p>
     *
     * @param messagePattern the message pattern that to be checked for placeholders.
     * @param arguments      the argument array to be converted.
     */
    public ParameterizedMessage(final String messagePattern, final Object[] arguments) {
        this.argArray = arguments;
        init(messagePattern, arguments == null ? 0 : arguments.length);
    }

    /**
     * Constructor with a pattern and a single parameter.
     * @param messagePattern The message pattern.
     * @param arg The parameter.
     */
    public ParameterizedMessage(final String messagePattern, final Object arg) {
        Object[] args = unrolledArgs();
        args[0] = arg;
        init(messagePattern, 1);
    }

    /**
     * Constructor with a pattern and two parameters.
     * @param messagePattern The message pattern.
     * @param arg0 The first parameter.
     * @param arg1 The second parameter.
     */
    public ParameterizedMessage(final String messagePattern, final Object arg0, final Object arg1) {
        Object[] args = unrolledArgs();
        args[0] = arg0;
        args[1] = arg1;
        init(messagePattern, 2);
    }

    public boolean isReused() {
        return reused;
    }

    void setReused(boolean reused) {
        this.reused = reused;
    }

    ParameterizedMessage set(String messagePattern, Object... arguments) {
        this.argArray = arguments;
        init(messagePattern, arguments == null ? 0 : arguments.length);
        return this;
    }

    private void init(String messagePattern, int argCount) {
        this.messagePattern = messagePattern;
        this.argCount = argCount;
        this.isThreadLocalMessageInitialized = false;
        int usedCount = countArgumentPlaceholders(messagePattern);
        initThrowable(getParameters(), usedCount);
    }

    private void initThrowable(final Object[] params, final int usedParams) {
        if (usedParams < argCount && this.throwable == null && params[argCount - 1] instanceof Throwable) {
            this.throwable = (Throwable) params[argCount - 1];
            argCount--;
        }
    }

    ParameterizedMessage set(String messagePattern, Object p0) {
        Object[] args = unrolledArgs();
        args[0] = p0;
        init(messagePattern, 1);
        return this;
    }

    ParameterizedMessage set(String messagePattern, Object p0, Object p1) {
        Object[] args = unrolledArgs();
        args[0] = p0;
        args[1] = p1;
        init(messagePattern, 2);
        return this;
    }

    ParameterizedMessage set(String messagePattern, Object p0, Object p1, Object p2) {
        Object[] args = unrolledArgs();
        args[0] = p0;
        args[1] = p1;
        args[2] = p2;
        init(messagePattern, 3);
        return this;
    }

    ParameterizedMessage set(String messagePattern, Object p0, Object p1, Object p2, Object p3) {
        Object[] args = unrolledArgs();
        args[0] = p0;
        args[1] = p1;
        args[2] = p2;
        args[3] = p3;
        init(messagePattern, 4);
        return this;
    }

    ParameterizedMessage set(String messagePattern, Object p0, Object p1, Object p2, Object p3, Object p4) {
        Object[] args = unrolledArgs();
        args[0] = p0;
        args[1] = p1;
        args[2] = p2;
        args[3] = p3;
        args[4] = p4;
        init(messagePattern, 5);
        return this;
    }

    ParameterizedMessage set(String messagePattern, Object p0, Object p1, Object p2, Object p3, Object p4, Object p5) {
        Object[] args = unrolledArgs();
        args[0] = p0;
        args[1] = p1;
        args[2] = p2;
        args[3] = p3;
        args[4] = p4;
        args[5] = p5;
        init(messagePattern, 6);
        return this;
    }

    ParameterizedMessage set(String messagePattern, Object p0, Object p1, Object p2, Object p3, Object p4, Object p5,
            Object p6) {
        Object[] args = unrolledArgs();
        args[0] = p0;
        args[1] = p1;
        args[2] = p2;
        args[3] = p3;
        args[4] = p4;
        args[5] = p5;
        args[6] = p6;
        init(messagePattern, 7);
        return this;
    }

    ParameterizedMessage set(String messagePattern, Object p0, Object p1, Object p2, Object p3, Object p4, Object p5,
            Object p6, Object p7) {
        Object[] args = unrolledArgs();
        args[0] = p0;
        args[1] = p1;
        args[2] = p2;
        args[3] = p3;
        args[4] = p4;
        args[5] = p5;
        args[6] = p6;
        args[7] = p7;
        init(messagePattern, 8);
        return this;
    }

    ParameterizedMessage set(String messagePattern, Object p0, Object p1, Object p2, Object p3, Object p4, Object p5,
            Object p6, Object p7, Object p8) {
        Object[] args = unrolledArgs();
        args[0] = p0;
        args[1] = p1;
        args[2] = p2;
        args[3] = p3;
        args[4] = p4;
        args[5] = p5;
        args[6] = p6;
        args[7] = p7;
        args[8] = p8;
        init(messagePattern, 9);
        return this;
    }

    ParameterizedMessage set(String messagePattern, Object p0, Object p1, Object p2, Object p3, Object p4, Object p5,
            Object p6, Object p7, Object p8, Object p9) {
        Object[] args = unrolledArgs();
        args[0] = p0;
        args[1] = p1;
        args[2] = p2;
        args[3] = p3;
        args[4] = p4;
        args[5] = p5;
        args[6] = p6;
        args[7] = p7;
        args[8] = p8;
        args[9] = p9;
        init(messagePattern, 10);
        return this;
    }

    private static Object[] unrolledArgs() {
        Object[] result = threadLocalUnrolledArgs.get();
        if (result == null) {
            result = new Object[10]; // array must be as big as number of unrolled varargs
            threadLocalUnrolledArgs.set(result);
        }
        return result;
    }

    private void clearUnrolledArgs() {
        final Object[] args = unrolledArgs();
        for (int i = 0; i < argCount; i++) {
            args[i] = null;
        }
    }

    /**
     * Returns the message pattern.
     * @return the message pattern.
     */
    @Override
    public String getFormat() {
        return messagePattern;
    }

    /**
     * Returns the message parameters.
     * @return the message parameters.
     */
    @Override
    public Object[] getParameters() {
        return argArray == null && argCount > 0 ? unrolledArgs() : argArray;
    }

    /**
     * Returns the Throwable that was given as the last argument, if any.
     * It will not survive serialization. The Throwable exists as part of the message
     * primarily so that it can be extracted from the end of the list of parameters
     * and then be added to the LogEvent. As such, the Throwable in the event should
     * not be used once the LogEvent has been constructed.
     *
     * @return the Throwable, if any.
     */
    @Override
    public Throwable getThrowable() {
        return throwable;
    }

    /**
     * Returns the formatted message.
     * @return the formatted message.
     */
    @Override
    public String getFormattedMessage() {
        if (!isThreadLocalMessageInitialized) {
            initFormattedMessage();
        }
        return threadLocalStringBuilder.get().toString();
    }

    private void initFormattedMessage() {
        final StringBuilder buffer = getThreadLocalStringBuilder();
        formatTo(buffer);
        isThreadLocalMessageInitialized = true;
    }

    private static StringBuilder getThreadLocalStringBuilder() {
        StringBuilder buffer = threadLocalStringBuilder.get();
        if (buffer == null) {
            buffer = new StringBuilder(255);
            threadLocalStringBuilder.set(buffer);
        }
        buffer.setLength(0);
        return buffer;
    }

    @Override
    public void formatTo(final StringBuilder buffer) {
        if (isThreadLocalMessageInitialized) {
            final StringBuilder msg = threadLocalStringBuilder.get();
            if (msg != buffer) {
                buffer.append(msg);
            }
            return;
        }
        formatMessage(buffer, messagePattern, getParameters(), argCount);
        clearUnrolledArgs();
    }

    /**
     * Replace placeholders in the given messagePattern with arguments.
     *
     * @param messagePattern the message pattern containing placeholders.
     * @param arguments      the arguments to be used to replace placeholders.
     * @return the formatted message.
     */
    public static String format(final String messagePattern, final Object[] arguments) {
        final StringBuilder result = getThreadLocalStringBuilder();
        final int argCount = arguments == null ? 0 : arguments.length;
        formatMessage(result, messagePattern, arguments, argCount);
        return result.toString();
    }

    /**
     * Replace placeholders in the given messagePattern with arguments.
     *
     * @param buffer the buffer to write the formatted message into
     * @param messagePattern the message pattern containing placeholders.
     * @param arguments      the arguments to be used to replace placeholders.
     */
    private static void formatMessage(final StringBuilder buffer, final String messagePattern,
            final Object[] arguments, final int argCount) {
        if (messagePattern == null || arguments == null || argCount == 0) {
            buffer.append(messagePattern);
            return;
        }
        int escapeCounter = 0;
        int currentArgument = 0;
        int i = 0;
        int len = messagePattern.length();
        for (; i < len - 1; i++) { // last char is excluded from the loop
            final char curChar = messagePattern.charAt(i);
            if (curChar == ESCAPE_CHAR) {
                escapeCounter++;
            } else {
                if (isDelimPair(curChar, messagePattern, i)) { // looks ahead one char
                    i++;

                    // write escaped escape chars
                    writeEscapedEscapeChars(escapeCounter, buffer);

                    if (isOdd(escapeCounter)) {
                        // i.e. escaped: write escaped escape chars
                        writeDelimPair(buffer);
                    } else {
                        // unescaped
                        writeArgOrDelimPair(arguments, argCount, currentArgument, buffer);
                        currentArgument++;
                    }
                } else {
                    handleLiteralChar(buffer, escapeCounter, curChar);
                }
                escapeCounter = 0;
            }
        }
        handleRemainingCharIfAny(messagePattern, len, buffer, escapeCounter, i);
    }

    /**
     * Returns {@code true} if the specified char and the char at {@code curCharIndex + 1} in the specified message
     * pattern together form a "{}" delimiter pair, returns {@code false} otherwise.
     */
    // Profiling showed this method is important to log4j performance. Modify with care!
    // 22 bytes (allows immediate JVM inlining: < 35 bytes) LOG4J2-1096
    private static boolean isDelimPair(final char curChar, final String messagePattern, final int curCharIndex) {
        return curChar == DELIM_START && messagePattern.charAt(curCharIndex + 1) == DELIM_STOP;
    }

    /**
     * Detects whether the message pattern has been fully processed or if an unprocessed character remains and processes
     * it if necessary, returning the resulting position in the result char array.
     */
    // Profiling showed this method is important to log4j performance. Modify with care!
    // 28 bytes (allows immediate JVM inlining: < 35 bytes) LOG4J2-1096
    private static void handleRemainingCharIfAny(final String messagePattern, final int len,
            final StringBuilder buffer, int escapeCounter, int i) {
        if (i == len - 1) {
            final char curChar = messagePattern.charAt(i);
            handleLastChar(buffer, escapeCounter, curChar);
        }
    }

    /**
     * Processes the last unprocessed character and returns the resulting position in the result char array.
     */
    // Profiling showed this method is important to log4j performance. Modify with care!
    // 28 bytes (allows immediate JVM inlining: < 35 bytes) LOG4J2-1096
    private static void handleLastChar(final StringBuilder buffer, final int escapeCounter, final char curChar) {
        if (curChar == ESCAPE_CHAR) {
            writeUnescapedEscapeChars(escapeCounter + 1, buffer);
        } else {
            handleLiteralChar(buffer, escapeCounter, curChar);
        }
    }

    /**
     * Processes a literal char (neither an '\' escape char nor a "{}" delimiter pair) and returns the resulting
     * position.
     */
    // Profiling showed this method is important to log4j performance. Modify with care!
    // 16 bytes (allows immediate JVM inlining: < 35 bytes) LOG4J2-1096
    private static void handleLiteralChar(final StringBuilder buffer, final int escapeCounter, final char curChar) {
        // any other char beside ESCAPE or DELIM_START/STOP-combo
        // write unescaped escape chars
        writeUnescapedEscapeChars(escapeCounter, buffer);
        buffer.append(curChar);
    }

    /**
     * Writes "{}" to the specified result array at the specified position and returns the resulting position.
     */
    // Profiling showed this method is important to log4j performance. Modify with care!
    // 18 bytes (allows immediate JVM inlining: < 35 bytes) LOG4J2-1096
    private static void writeDelimPair(final StringBuilder buffer) {
        buffer.append(DELIM_START);
        buffer.append(DELIM_STOP);
    }

    /**
     * Returns {@code true} if the specified parameter is odd.
     */
    // Profiling showed this method is important to log4j performance. Modify with care!
    // 11 bytes (allows immediate JVM inlining: < 35 bytes) LOG4J2-1096
    private static boolean isOdd(final int number) {
        return (number & 1) == 1;
    }

    /**
     * Writes a '\' char to the specified result array (starting at the specified position) for each <em>pair</em> of
     * '\' escape chars encountered in the message format and returns the resulting position.
     */
    // Profiling showed this method is important to log4j performance. Modify with care!
    // 11 bytes (allows immediate JVM inlining: < 35 bytes) LOG4J2-1096
    private static void writeEscapedEscapeChars(final int escapeCounter, final StringBuilder buffer) {
        final int escapedEscapes = escapeCounter >> 1; // divide by two
        writeUnescapedEscapeChars(escapedEscapes, buffer);
    }

    /**
     * Writes the specified number of '\' chars to the specified result array (starting at the specified position) and
     * returns the resulting position.
     */
    // Profiling showed this method is important to log4j performance. Modify with care!
    // 20 bytes (allows immediate JVM inlining: < 35 bytes) LOG4J2-1096
    private static void writeUnescapedEscapeChars(int escapeCounter, final StringBuilder buffer) {
        while (escapeCounter > 0) {
            buffer.append(ESCAPE_CHAR);
            escapeCounter--;
        }
    }

    /**
     * Appends the argument at the specified argument index (or, if no such argument exists, the "{}" delimiter pair) to
     * the specified result char array at the specified position and returns the resulting position.
     */
    // Profiling showed this method is important to log4j performance. Modify with care!
    // 25 bytes (allows immediate JVM inlining: < 35 bytes) LOG4J2-1096
    private static void writeArgOrDelimPair(final Object[] arguments, final int argCount, final int currentArgument,
            final StringBuilder buffer) {
        if (currentArgument < argCount) {
            recursiveDeepToString(arguments[currentArgument], buffer, null);
        } else {
            writeDelimPair(buffer);
        }
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        final ParameterizedMessage that = (ParameterizedMessage) o;

        if (messagePattern != null ? !messagePattern.equals(that.messagePattern) : that.messagePattern != null) {
            return false;
        }
        if (!Arrays.equals(this.argArray, that.argArray)) {
            return false;
        }
        //if (throwable != null ? !throwable.equals(that.throwable) : that.throwable != null) return false;

        return true;
    }

    @Override
    public int hashCode() {
        int result = messagePattern != null ? messagePattern.hashCode() : 0;
        result = HASHVAL * result + (argArray != null ? Arrays.hashCode(argArray) : 0);
        return result;
    }

    /**
     * Counts the number of unescaped placeholders in the given messagePattern.
     *
     * @param messagePattern the message pattern to be analyzed.
     * @return the number of unescaped placeholders.
     */
    public static int countArgumentPlaceholders(final String messagePattern) {
        if (messagePattern == null) {
            return 0;
        }
        int length = messagePattern.length();
        int result = 0;
        boolean isEscaped = false;
        for (int i = 0; i < length - 1; i++) {
            final char curChar = messagePattern.charAt(i);
            if (curChar == ESCAPE_CHAR) {
                isEscaped = !isEscaped;
            } else if (curChar == DELIM_START) {
                if (!isEscaped && messagePattern.charAt(i + 1) == DELIM_STOP) {
                    result++;
                    i++;
                }
                isEscaped = false;
            } else {
                isEscaped = false;
            }
        }
        return result;
    }

    /**
     * This method performs a deep toString of the given Object.
     * Primitive arrays are converted using their respective Arrays.toString methods while
     * special handling is implemented for "container types", i.e. Object[], Map and Collection because those could
     * contain themselves.
     * <p>
     * It should be noted that neither AbstractMap.toString() nor AbstractCollection.toString() implement such a
     * behavior. They only check if the container is directly contained in itself, but not if a contained container
     * contains the original one. Because of that, Arrays.toString(Object[]) isn't safe either.
     * Confusing? Just read the last paragraph again and check the respective toString() implementation.
     * </p>
     * <p>
     * This means, in effect, that logging would produce a usable output even if an ordinary System.out.println(o)
     * would produce a relatively hard-to-debug StackOverflowError.
     * </p>
     * @param o The object.
     * @return The String representation.
     */
    public static String deepToString(final Object o) {
        if (o == null) {
            return null;
        }
        if (o instanceof String) {
            return (String) o;
        }
        final StringBuilder str = new StringBuilder();
        final Set<String> dejaVu = new HashSet<>(); // that's actually a neat name ;)
        recursiveDeepToString(o, str, dejaVu);
        return str.toString();
    }

    /**
     * This method performs a deep toString of the given Object.
     * Primitive arrays are converted using their respective Arrays.toString methods while
     * special handling is implemented for "container types", i.e. Object[], Map and Collection because those could
     * contain themselves.
     * <p>
     * dejaVu is used in case of those container types to prevent an endless recursion.
     * </p>
     * <p>
     * It should be noted that neither AbstractMap.toString() nor AbstractCollection.toString() implement such a
     * behavior.
     * They only check if the container is directly contained in itself, but not if a contained container contains the
     * original one. Because of that, Arrays.toString(Object[]) isn't safe either.
     * Confusing? Just read the last paragraph again and check the respective toString() implementation.
     * </p>
     * <p>
     * This means, in effect, that logging would produce a usable output even if an ordinary System.out.println(o)
     * would produce a relatively hard-to-debug StackOverflowError.
     * </p>
     *
     * @param o      the Object to convert into a String
     * @param str    the StringBuilder that o will be appended to
     * @param dejaVu a list of container identities that were already used.
     */
    private static void recursiveDeepToString(final Object o, final StringBuilder str, final Set<String> dejaVu) {
        if (appendSpecialTypes(o, str)) {
            return;
        }
        if (isMaybeRecursive(o)) {
            appendPotentiallyRecursiveValue(o, str, dejaVu);
        } else {
            tryObjectToString(o, str);
        }
    }

    private static boolean appendSpecialTypes(final Object o, final StringBuilder str) {
        if (o == null || o instanceof String) {
            str.append((String) o);
            return true;
        } else if (o instanceof StringBuilder) {
            str.append((StringBuilder) o);
            return true;
        } else if (o instanceof StringBuilderFormattable) {
            ((StringBuilderFormattable) o).formatTo(str);
            return true;
        }
        return appendDate(o, str);
    }

    private static boolean appendDate(final Object o, final StringBuilder str) {
        if (!(o instanceof Date)) {
            return false;
        }
        final Date date = (Date) o;
        final SimpleDateFormat format = getSimpleDateFormat();
        str.append(format.format(date));
        return true;
    }

    private static SimpleDateFormat getSimpleDateFormat() {
        SimpleDateFormat result = threadLocalSimpleDateFormat.get();
        if (result == null) {
            result = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSZ");
            threadLocalSimpleDateFormat.set(result);
        }
        return result;
    }

    /**
     * Returns {@code true} if the specified object is an array, a Map or a Collection.
     */
    private static boolean isMaybeRecursive(final Object o) {
        return o.getClass().isArray() || o instanceof Map || o instanceof Collection;
    }

    private static void appendPotentiallyRecursiveValue(final Object o, final StringBuilder str, Set<String> dejaVu) {
        if (dejaVu == null) {
            dejaVu = new HashSet<>();
        }
        final Class<?> oClass = o.getClass();
        if (oClass.isArray()) {
            appendArray(o, str, dejaVu, oClass);
        } else if (o instanceof Map) {
            appendMap(o, str, dejaVu);
        } else if (o instanceof Collection) {
            appendCollection(o, str, dejaVu);
        }
    }

    private static void appendArray(final Object o, final StringBuilder str, final Set<String> dejaVu,
            final Class<?> oClass) {
        if (oClass == byte[].class) {
            str.append(Arrays.toString((byte[]) o));
        } else if (oClass == short[].class) {
            str.append(Arrays.toString((short[]) o));
        } else if (oClass == int[].class) {
            str.append(Arrays.toString((int[]) o));
        } else if (oClass == long[].class) {
            str.append(Arrays.toString((long[]) o));
        } else if (oClass == float[].class) {
            str.append(Arrays.toString((float[]) o));
        } else if (oClass == double[].class) {
            str.append(Arrays.toString((double[]) o));
        } else if (oClass == boolean[].class) {
            str.append(Arrays.toString((boolean[]) o));
        } else if (oClass == char[].class) {
            str.append(Arrays.toString((char[]) o));
        } else {
            // special handling of container Object[]
            final String id = identityToString(o);
            if (dejaVu.contains(id)) {
                str.append(RECURSION_PREFIX).append(id).append(RECURSION_SUFFIX);
            } else {
                dejaVu.add(id);
                final Object[] oArray = (Object[]) o;
                str.append('[');
                boolean first = true;
                for (final Object current : oArray) {
                    if (first) {
                        first = false;
                    } else {
                        str.append(", ");
                    }
                    recursiveDeepToString(current, str, new HashSet<>(dejaVu));
                }
                str.append(']');
            }
            //str.append(Arrays.deepToString((Object[]) o));
        }
    }

    private static void appendMap(final Object o, final StringBuilder str, final Set<String> dejaVu) {
        // special handling of container Map
        final String id = identityToString(o);
        if (dejaVu.contains(id)) {
            str.append(RECURSION_PREFIX).append(id).append(RECURSION_SUFFIX);
        } else {
            dejaVu.add(id);
            final Map<?, ?> oMap = (Map<?, ?>) o;
            str.append('{');
            boolean isFirst = true;
            for (final Object o1 : oMap.entrySet()) {
                final Map.Entry<?, ?> current = (Map.Entry<?, ?>) o1;
                if (isFirst) {
                    isFirst = false;
                } else {
                    str.append(", ");
                }
                final Object key = current.getKey();
                final Object value = current.getValue();
                recursiveDeepToString(key, str, new HashSet<>(dejaVu));
                str.append('=');
                recursiveDeepToString(value, str, new HashSet<>(dejaVu));
            }
            str.append('}');
        }
    }

    private static void appendCollection(final Object o, final StringBuilder str, final Set<String> dejaVu) {
        // special handling of container Collection
        final String id = identityToString(o);
        if (dejaVu.contains(id)) {
            str.append(RECURSION_PREFIX).append(id).append(RECURSION_SUFFIX);
        } else {
            dejaVu.add(id);
            final Collection<?> oCol = (Collection<?>) o;
            str.append('[');
            boolean isFirst = true;
            for (final Object anOCol : oCol) {
                if (isFirst) {
                    isFirst = false;
                } else {
                    str.append(", ");
                }
                recursiveDeepToString(anOCol, str, new HashSet<>(dejaVu));
            }
            str.append(']');
        }
    }

    private static void tryObjectToString(final Object o, final StringBuilder str) {
        // it's just some other Object, we can only use toString().
        try {
            str.append(o.toString());
        } catch (final Throwable t) {
            handleErrorInObjectToString(o, str, t);
        }
    }

    private static void handleErrorInObjectToString(final Object o, final StringBuilder str, final Throwable t) {
        str.append(ERROR_PREFIX);
        str.append(identityToString(o));
        str.append(ERROR_SEPARATOR);
        final String msg = t.getMessage();
        final String className = t.getClass().getName();
        str.append(className);
        if (!className.equals(msg)) {
            str.append(ERROR_MSG_SEPARATOR);
            str.append(msg);
        }
        str.append(ERROR_SUFFIX);
    }

    /**
     * This method returns the same as if Object.toString() would not have been
     * overridden in obj.
     * <p>
     * Note that this isn't 100% secure as collisions can always happen with hash codes.
     * </p>
     * <p>
     * Copied from Object.hashCode():
     * </p>
     * <blockquote>
     * As much as is reasonably practical, the hashCode method defined by
     * class {@code Object} does return distinct integers for distinct
     * objects. (This is typically implemented by converting the internal
     * address of the object into an integer, but this implementation
     * technique is not required by the Java&#8482; programming language.)
     * </blockquote>
     *
     * @param obj the Object that is to be converted into an identity string.
     * @return the identity string as also defined in Object.toString()
     */
    public static String identityToString(final Object obj) {
        if (obj == null) {
            return null;
        }
        return obj.getClass().getName() + '@' + Integer.toHexString(System.identityHashCode(obj));
    }

    @Override
    public String toString() {
        return getFormattedMessage();
    }
}
