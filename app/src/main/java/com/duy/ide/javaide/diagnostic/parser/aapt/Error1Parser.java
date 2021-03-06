/*
 * Copyright (C) 2018 Tran Le Duy
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */
package com.duy.ide.javaide.diagnostic.parser.aapt;

import com.android.annotations.NonNull;
import com.duy.ide.diagnostic.model.Message;
import com.duy.ide.diagnostic.parser.ParsingFailedException;
import com.duy.ide.diagnostic.util.OutputLineReader;
import com.duy.ide.logging.ILogger;
import com.google.common.collect.ImmutableList;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

class Error1Parser extends AbstractAaptOutputParser {

    /**
     * First and second line of dual-line aapt error.
     * <pre>
     * ERROR at line &lt;line&gt;: &lt;error&gt;
     *  (Occurred while parsing &lt;path&gt;)
     * </pre>
     */
    private static final List<Pattern> MSG_PATTERNS = ImmutableList.of(
            Pattern.compile("^ERROR\\s+at\\s+line\\s+(\\d+):\\s+(.*)$"),
            Pattern.compile("^\\s+\\(Occurred while parsing\\s+(.*)\\)$")
    );

    @Override
    public boolean parse(@NonNull String line, @NonNull OutputLineReader reader, @NonNull List<Message> messages, @NonNull ILogger logger)
            throws ParsingFailedException {
        Matcher m = MSG_PATTERNS.get(0).matcher(line);
        if (!m.matches()) {
            return false;
        }
        String lineNumber = m.group(1);
        String msgText = m.group(2);

        m = getNextLineMatcher(reader, MSG_PATTERNS.get(1));
        if (m == null) {
            throw new ParsingFailedException();
        }
        String sourcePath = m.group(1);

        Message msg = createMessage(Message.Kind.ERROR, msgText, sourcePath,
                lineNumber, "", logger);
        messages.add(msg);
        return true;
    }
}
