DROP VIEW IF EXISTS q0, q1i, q1ii, q1iii, q1iv, q2i, q2ii, q2iii, q3i, q3ii, q3iii, q4i, q4ii, q4iii, q4iv, q4v;

-- Question 0
CREATE VIEW q0(era)
AS
  SELECT MAX(era) FROM pitching
;

-- Question 1i
CREATE VIEW q1i(namefirst, namelast, birthyear)
AS
  SELECT namefirst, namelast, birthyear
  FROM people WHERE weight > 300
;

-- Question 1ii
CREATE VIEW q1ii(namefirst, namelast, birthyear)
AS
  SELECT namefirst, namelast, birthyear
  FROM people WHERE namefirst ~ '[a-z | A-Z]+.* [a-z | A-Z]+.*'
;

-- Question 1iii
CREATE VIEW q1iii(birthyear, avgheight, count)
AS
  SELECT birthyear, avg(height), count(*)
  FROM people
  GROUP BY birthyear
  ORDER BY birthyear
;

-- Question 1iv
CREATE VIEW q1iv(birthyear, avgheight, count)
AS
  SELECT birthyear, avg(height), count(*)
  FROM people
  GROUP BY birthyear
  HAVING avg(height) > 70
  ORDER BY birthyear
;

-- Question 2i
CREATE VIEW q2i(namefirst, namelast, playerid, yearid)
AS
  SELECT namefirst, namelast, p.playerid, yearid
  FROM people p NATURAL JOIN HallOfFame hof
  WHERE inducted = 'Y'
  ORDER BY yearid DESC
;


-- Question 2ii
CREATE VIEW q2ii(namefirst, namelast, playerid, schoolid, yearid)
AS                                                    --yearid ambiguous?
  SELECT namefirst, namelast, p.playerid, cp.schoolid, hof.yearid
  FROM people p, HallOfFame hof, collegeplaying cp, schools s
  WHERE p.playerid = hof.playerid and hof.playerid = cp.playerid and cp.schoolid = s.schoolid and inducted = 'Y' and schoolState = 'CA'
  ORDER BY yearid DESC, cp.schoolid, p.playerid
;

--Remember you can use the views..

CREATE VIEW q2iii(playerid, namefirst, namelast, schoolid)
AS
  SELECT p.playerid, namefirst, namelast, cp.schoolid
  FROM people p, halloffame hof LEFT OUTER JOIN collegeplaying cp on hof.playerid = cp.playerid
  WHERE p.playerid = hof.playerid and hof.inducted = 'Y'
  ORDER BY p.playerid DESC, schoolid
;

-- Question 3i
CREATE VIEW q3i(playerid, namefirst, namelast, yearid, slg)
AS
  SELECT p.playerID, nameFirst, nameLast, yearID, CAST((H - (H2B + H3B + HR)) + 2 * H2B + 3 * H3B + 4 * HR AS FLOAT) /CAST (AB AS FLOAT) as slg
  FROM People as p, Batting as b
  WHERE p.playerID = b.playerID and AB > 50
  ORDER BY slg DESC, yearID ASC, playerID ASC
  LIMIT 10
;

-- Question 3ii
CREATE VIEW q3ii(playerid, namefirst, namelast, lslg)
AS

  WITH totals(playerid, h, h2b, h3b, hr, ab) AS (
    SELECT p.playerid, sum(h), sum(h2b), sum(h3b), sum(hr), sum(ab)
    FROM people as p, batting as b
    WHERE p.playerid = b.playerid
    GROUP BY p.playerid
  )

  SELECT t.playerid, namefirst, namelast, CAST((H - (H2B + H3B + HR)) + 2 * H2B + 3 * H3B + 4 * HR AS FLOAT) /CAST (AB AS FLOAT) as lslg
  FROM totals t, people p
  WHERE p.playerid = t.playerid and ab > 50
  ORDER BY lslg DESC, playerid
  LIMIT 10
;

-- Question 3iii
CREATE VIEW q3iii(namefirst, namelast, lslg)
AS
  WITH lslgs(playerid, namefirst, namelast, lslg) AS (
    SELECT playerid, namefirst, namelast, CAST((H - (H2B + H3B + HR)) + 2 * H2B + 3 * H3B + 4 * HR AS FLOAT) /CAST (AB AS FLOAT) FROM (
      SELECT p.playerid, namefirst, namelast, sum(h) as h, sum(h2b) as h2b, sum(h3b) as h3b, sum(hr) as hr, sum(ab) as ab
      FROM people as p, batting as b
      WHERE p.playerid = b.playerid
      GROUP BY p.playerid
    ) as totals
    WHERE ab > 50
  )
  SELECT namefirst, namelast, lslg
  FROM lslgs
  WHERE lslg > (SELECT lslg
                FROM lslgs
                WHERE playerid = 'mayswi01')
;


-- Question 4i
CREATE VIEW q4i(yearid, min, max, avg, stddev)
AS
  SELECT s.yearid, min(salary), max(salary), avg(salary), stddev(salary)
  FROM people p NATURAL JOIN salaries s
  GROUP BY s.yearid
  ORDER BY s.yearid
;

-- Question 4ii
CREATE VIEW q4ii(binid, low, high, count)
AS
  WITH q AS (
      SELECT min(salary) as low, max(salary) as high, (max(salary) - min(salary))/10 as range
      FROM salaries
      WHERE yearid = 2016
  ), q2 as (
      SELECT LEAST(floor( (salary - low)/range), 9) as binid
      FROM salaries, q
      WHERE yearid = 2016
  )

  SELECT binid, low + (binid * range), low + (binid+1) * range, count(*)
  FROM q, q2
  GROUP BY binid, low, range
  ORDER BY binid
;

-- Question 4iii
CREATE VIEW q4iii(yearid, mindiff, maxdiff, avgdiff)
AS
  SELECT q.yearid, q.min - i.min as mindiff, q.max - i.max as maxdiff, q.avg - i.avg as avgdiff
  FROM q4i as q, q4i as i
  WHERE q.yearid = i.yearid + 1
  ORDER BY q.yearid
;

-- Question 4iv
CREATE VIEW q4iv(playerid, namefirst, namelast, salary, yearid)
AS
  WITH m2000(max) AS (
    SELECT max(salary)
    FROM salaries s
    WHERE yearid = 2000
  ), m2001(max) AS (
    SELECT max(salary)
    FROM salaries s
    WHERE yearid = 2001
  )

  SELECT s.playerid, namefirst, namelast, salary, s.yearid
  FROM salaries s, people p, m2000 m
  WHERE s.playerid = p.playerid and s.salary = m.max and s.yearid = 2000

  UNION

  SELECT s.playerid, namefirst, namelast, salary, s.yearid
  FROM salaries s, people p, m2001 m
  WHERE s.playerid = p.playerid and s.salary = m.max and s.yearid = 2001
;
-- Question 4v
CREATE VIEW q4v(team, diffAvg) AS
  SELECT teamID team, max(salary) - min(salary) as diffAvg
  FROM allstarfull a NATURAL JOIN salaries s
  WHERE yearid = 2016
  GROUP BY teamID
  ORDER BY teamid
;
