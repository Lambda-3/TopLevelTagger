package org.lambda3.tagger;

/*
 * ==========================License-Start=============================
 * Top Level Tagger
 *
 * Copyright © 2017 Lambda³
 *
 * GNU General Public License 3
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
 * along with this program.  If not, see http://www.gnu.org/licenses/.
 * ==========================License-End==============================
 */

public final class WordMapping {

    private String word;
    private String label;

    public WordMapping(String word, String label) {
        this.word = word;
        this.label = label;
    }

    public String getWord() {
        return word;
    }

    public String getLabel() {
        return label;
    }
}
