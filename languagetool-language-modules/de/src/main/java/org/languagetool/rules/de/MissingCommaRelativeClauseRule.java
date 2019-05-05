/* LanguageTool, a natural language style checker 
 * Copyright (C) 2005 Daniel Naber (http://www.danielnaber.de)
 * 
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA  02110-1301
 * USA
 */
package org.languagetool.rules.de;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.ResourceBundle;
import java.util.regex.Pattern;

import org.apache.commons.lang3.StringUtils;
import org.languagetool.AnalyzedSentence;
import org.languagetool.AnalyzedTokenReadings;
import org.languagetool.rules.Category;
import org.languagetool.rules.CategoryId;
import org.languagetool.rules.Rule;
import org.languagetool.rules.RuleMatch;
import org.languagetool.rules.Category.Location;

/**
 * A rule checks a sentence for a missing comma before or after a relative clause (only for German language)
 * @author Fred Kruse
 */
public class MissingCommaRelativeClauseRule extends Rule {

  private static final Pattern MARKS_REGEX = Pattern.compile("[,;.:?!-–—’'\"„“”»«‚‘›‹()\\[\\]]");
  
  final boolean after;   // TODO: advance rule to check also for a missing comma after relative clause

  public MissingCommaRelativeClauseRule(ResourceBundle messages) {
    this(messages, false);
  }

  public MissingCommaRelativeClauseRule(ResourceBundle messages, boolean after) {
    super(messages);
    super.setCategory(new Category(new CategoryId("HILFESTELLUNG_KOMMASETZUNG"), 
        "Hilfestellung für Kommasetzung", Location.INTERNAL, true));
    this.after = after;
  }

  @Override
  public String getId() {
    return (after ? "COMMA_AFTER_RELATIVE_CLAUSE" : "COMMA_BEFORE_RELATIVE_CLAUSE");
  }

  @Override
  public String getDescription() {
    return (after? "Fehlendes Komma nach Relativsatz" : "Fehlendes Komma vor Relativsatz");
  }

/**
 * is a separator
 */
  static boolean isSeparator(String token) {
    return (MARKS_REGEX.matcher(token).matches() || token.equals("und") || token.equals("oder"));
  }

/**
 * get the position of the next separator
 */
  static int nextSeparator(AnalyzedTokenReadings[] tokens, int start) {
    for(int i = start; i < tokens.length; i++) {
      if(isSeparator(tokens[i].getToken())) {
        return i;
      }
    }
    return tokens.length - 1;
  }

/**
 * is Präposition  
 */
  static boolean isPrp(AnalyzedTokenReadings token) {
    return token.hasPosTagStartingWith("PRP:");
  }
  
/**
 * is a potential verb used in sentence or subclause
 */
  static boolean isVerb(AnalyzedTokenReadings[] tokens, int n) {
    return (tokens[n].matchesPosTagRegex("(VER:[1-3]:|VER:.*:[1-3]:).*") 
        && !tokens[n].matchesPosTagRegex("(ZAL|ADV|ART|SUB).*")
        && (!tokens[n].matchesPosTagRegex("VER:INF:.*") || !tokens[n-1].getToken().equals("zu"))
      );
  }

/**
 * is any verb but not an "Infinitiv mit zu"
 */
  static boolean isAnyVerb(AnalyzedTokenReadings[] tokens, int n) {
    return tokens[n].matchesPosTagRegex("VER:.*") 
        || (n < tokens.length - 1 
            && ((tokens[n].getToken().equals("zu") && tokens[n+1].matchesPosTagRegex("VER:INF:.*"))
             || (tokens[n].hasPosTagStartingWith("NEG") && tokens[n+1].matchesPosTagRegex("VER:.*")))); 
  }
  
/**
 * is a verb after sub clause
 */
  static boolean isVerbAfter(AnalyzedTokenReadings[] tokens, int end) {
    return (end < tokens.length - 1 && tokens[end].getToken().equals(",") && tokens[end+1].hasPosTagStartingWith("VER:"));
  }
  
/**
 * gives the positions of verbs in a subclause
 */
  static List<Integer> verbPos(AnalyzedTokenReadings[] tokens, int start, int end) {
    List<Integer>verbs = new ArrayList<>();
    for(int i = start; i < end; i++) {
      if(isVerb(tokens, i)) {
        if(tokens[i].matchesPosTagRegex("PA[12]:.*")) {
          String gender = getGender(tokens[i]);
          String sStr = "(ADJ|PA[12]):.*" + gender +".*";
          int j;
          for(j = i + 1; j < end && tokens[j].matchesPosTagRegex(sStr); j++);
          if(!tokens[j].matchesPosTagRegex("(SUB|EIG):.*" + gender +".*") && !tokens[j].isPosTagUnknown()) {
            verbs.add(i);
          }
        } else {
          verbs.add(i);
        }
      }
    }
    return verbs;
  }
  
/**
 * first token initiate a subclause
 */
  static boolean isKonUnt(AnalyzedTokenReadings token) {
    return (token.hasPosTagStartingWith("KON:UNT") 
        || StringUtils.equalsAnyIgnoreCase(token.getToken(), "wer", "wo", "wohin"));
  }
  

/**
 * checks to what position a test of relative clause should done
 * return -1 if no potential relative clause is assumed
 */
  static int hasPotentialSubclause(AnalyzedTokenReadings[] tokens, int start, int end) {
    List<Integer> verbs = verbPos(tokens, start, end);
    if(verbs.size() == 1 && end < tokens.length - 2 && verbs.get(0) == end - 1) {
      int nextEnd = nextSeparator(tokens, end + 1);
      List<Integer> nextVerbs = verbPos(tokens, end + 1, nextEnd);
      if(isKonUnt(tokens[start])) {
        if(nextVerbs.size() > 1 || (nextVerbs.size() == 1 && nextVerbs.get(0) == end - 1)) {
          return verbs.get(0);
        }
      } else if(nextVerbs.size() > 0) {
        return verbs.get(0);
      }
      return -1;
    }
    if(verbs.size() == 2) {
      if(tokens[verbs.get(0)].matchesPosTagRegex("VER:(MOD|AUX):.*") && tokens[verbs.get(1)].matchesPosTagRegex("VER:INF:.*")) {
        return verbs.get(0);
      }
      if(tokens[verbs.get(0)].matchesPosTagRegex("VER:AUX:.*") && tokens[verbs.get(1)].matchesPosTagRegex("VER:PA2:.*")) {
        return -1;
      }
    }
    if(verbs.size() == 3) {
      if(tokens[verbs.get(0)].matchesPosTagRegex("VER:MOD:.*") 
          && ((tokens[verbs.get(2) - 1].matchesPosTagRegex("VER:(INF|PA2):.*") && tokens[verbs.get(2)].matchesPosTagRegex("VER:INF:.*"))
              || (tokens[verbs.get(1) - 1].getToken().equals("weder") && tokens[verbs.get(1)].matchesPosTagRegex("VER:INF:.*")
                  && tokens[verbs.get(2) - 1].getToken().equals("noch") && tokens[verbs.get(1)].matchesPosTagRegex("VER:INF:.*")))
        ) {
        return -1;
      }
    }
    if(verbs.size() > 1) {
      return verbs.get(verbs.size() - 1);
    }
    return -1;
  }
  
/**
 * is potential relative pronoun 
 */
  static boolean isPronoun(AnalyzedTokenReadings[] tokens, int n) {
    return tokens[n].getToken().matches("(d(e[mnr]|ie|as|essen|e[nr]en)|welche[mrs]?|wessen)");
  }
  
/**
 * get the gender of of a token
 */
  static String getGender(AnalyzedTokenReadings token) {
    int nMatches = 0;
    String ret = "";
    if(token.matchesPosTagRegex(".*:SIN:FEM.*")) {
      ret += "SIN:FEM";
      nMatches++;
    }
    if(token.matchesPosTagRegex(".*:SIN:MAS.*")) {
      if(nMatches > 0) {
        ret += "|";
      }
      ret += "SIN:MAS";
      nMatches++;
    }
    if(token.matchesPosTagRegex(".*:SIN:NEU.*")) {
      if(nMatches > 0) {
        ret += "|";
      }
      ret += "SIN:NEU";
      nMatches++;
    }
    if(token.matchesPosTagRegex(".*:PLU.*")) {
      if(nMatches > 0) {
        ret += "|";
      }
      ret += "PLU";
      nMatches++;
    }
    if(nMatches > 1) {
      ret = "(" + ret + ")";
    }
    return ret;
  }
  
/**
 * does the gender match with a subject or name?
 */
  static boolean matchesGender(String gender, AnalyzedTokenReadings[] tokens, int from, int to) {
    String mStr = "(SUB|EIG):.*" + gender +".*";
    for (int i = to - 1; i >= from; i-- ) {
      if(tokens[i].matchesPosTagRegex(mStr) && (i != 1 || !tokens[i].hasPosTagStartingWith("VER:"))) {
        return true;
      }
    }
    return false;
  }

/**
 * is the token a potential article without a noun
 */
  static boolean isArticleWithoutSub(String gender, AnalyzedTokenReadings[] tokens, int n) {
    if(tokens[n].matchesPosTagRegex("VER:.*") && tokens[n-1].matchesPosTagRegex("(ADJ|PRO:POS):.*" + gender +".*")) {
      return true;
    }
    return false;
  }
  
/**
 * skip tokens till the next noun
 * check for e.g. "das in die dunkle Garage fahrende Auto" -> "das" is article
 */
  static int skipToSub(String gender, AnalyzedTokenReadings[] tokens, int n, int to) {
    if(tokens[n+1].matchesPosTagRegex("PA[12]:.*" + gender + ".*")) {
      return n+1;
    }
    for(int i = n + 1; i < to; i++) {
      if(tokens[i].matchesPosTagRegex("(ADJ|PA[12]):.*" + gender + ".*")) {
        return i;
      }
    }
    return -1;
  }

/**
 * check if token is potentially an article
 */
  static boolean isArticle(String gender, AnalyzedTokenReadings[] tokens, int from, int to) {
    if(tokens[from].getToken().matches("(welche[mrs]?|wessen)")) {
      return false;
    }
    String sSub = "(SUB|EIG):.*" + gender +".*";
    String sAdj = "(ZAL|PRP:|KON:|ADV:|ADJ:PRD:|(ADJ|PA[12]|PRO:(POS|DEM|IND)):.*" + gender +").*";
    for (int i = from + 1; i < to; i++ ) {
      if(tokens[i].matchesPosTagRegex(sSub) || tokens[i].isPosTagUnknown()) {
        return true;
      }
      if(tokens[i].hasPosTagStartingWith("ART") || !tokens[i].matchesPosTagRegex(sAdj)) {
        if(isArticleWithoutSub(gender, tokens, i)) {
          return true;
        }
        int skipTo = skipToSub(gender, tokens, i, to);
        if(skipTo > 0) {
          i = skipTo;
        } else {
          return false;
        }
      }
    }
    if(to < tokens.length && isArticleWithoutSub(gender, tokens, to)) {
      return true;
    }
    return false;
  }

/**
 * gives back position where a comma is missed
 * PRP has to be treated separately
 */
  static int missedCommaBefore(AnalyzedTokenReadings[] tokens, int start, int end, int lastVerb) {
    for(int i = start; i < lastVerb - 1; i++) {
      if(isPronoun(tokens, i)) {
        String gender = getGender(tokens[i]);
        if(gender != null && !isAnyVerb(tokens, i + 1) 
            && matchesGender(gender, tokens, start, i) && !isArticle(gender, tokens, i, lastVerb)
            ) {
          return i;
        }
      }
    }
    return -1;
  }
  
  @Override
  public RuleMatch[] match(AnalyzedSentence sentence) throws IOException {
    List<RuleMatch> ruleMatches = new ArrayList<>();
    AnalyzedTokenReadings[] tokens = sentence.getTokensWithoutWhitespace();
    if(tokens.length <= 1) {
      return toRuleMatchArray(ruleMatches);
    }
    int subStart = 1;
    if(isSeparator(tokens[subStart].getToken())) {
      subStart++;
    }
    while (subStart < tokens.length) {
      int subEnd = nextSeparator(tokens, subStart);
      int lastVerb = hasPotentialSubclause(tokens, subStart, subEnd);
      if(lastVerb > 0) {
        int nToken = missedCommaBefore(tokens, subStart, subEnd, lastVerb);
        if( nToken > 0) {
          int startToken = nToken - (isPrp(tokens[nToken - 1]) ? 2 : 1);
          RuleMatch match = new RuleMatch(this, sentence, tokens[startToken].getStartPos(), tokens[nToken].getEndPos(), 
              "Sollten Sie hier ein Komma einfügen (Relativsatz)?");
          if(nToken - startToken > 1) {
            match.setSuggestedReplacement(tokens[startToken].getToken() + ", " + tokens[nToken - 1].getToken() + " " + tokens[nToken].getToken());
          } else {
            match.setSuggestedReplacement(tokens[startToken].getToken() + ", " + tokens[nToken].getToken());
          }
          ruleMatches.add(match);
        }
      }
      subStart = subEnd + 1;
    }
    return toRuleMatchArray(ruleMatches);
  }

}