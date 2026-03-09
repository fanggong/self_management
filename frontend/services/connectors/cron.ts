type CronFieldResult = {
  allowed: Set<number>;
  wildcard: boolean;
};

type NextRunResult = {
  success: boolean;
  nextRun?: Date;
  message?: string;
};

type UpcomingRunsResult = {
  success: boolean;
  runs?: Date[];
  message?: string;
};

type ParseDateTimeResult = {
  success: boolean;
  date?: Date;
  message?: string;
};

const SHANGHAI_OFFSET_MINUTES = 8 * 60;

const toShanghaiProxyDate = (value: Date) => {
  return new Date(value.getTime() + SHANGHAI_OFFSET_MINUTES * 60 * 1000);
};

const fromShanghaiParts = (
  year: number,
  month: number,
  day: number,
  hour: number,
  minute: number,
  second = 0
) => {
  return new Date(Date.UTC(year, month - 1, day, hour, minute, second) - SHANGHAI_OFFSET_MINUTES * 60 * 1000);
};

const toShanghaiParts = (value: Date) => {
  const shanghaiValue = toShanghaiProxyDate(value);

  return {
    year: shanghaiValue.getUTCFullYear(),
    month: shanghaiValue.getUTCMonth() + 1,
    day: shanghaiValue.getUTCDate(),
    hour: shanghaiValue.getUTCHours(),
    minute: shanghaiValue.getUTCMinutes(),
    second: shanghaiValue.getUTCSeconds()
  };
};

const parseCronField = (
  value: string,
  min: number,
  max: number,
  normalize?: (input: number) => number
): CronFieldResult => {
  const trimmed = value.trim();
  if (!trimmed) {
    throw new Error('Each cron field must be provided.');
  }

  const result = new Set<number>();
  const wildcard = trimmed === '*';

  if (wildcard) {
    for (let current = min; current <= max; current += 1) {
      result.add(normalize ? normalize(current) : current);
    }

    return {
      allowed: result,
      wildcard: true
    };
  }

  const segments = trimmed.split(',');
  for (const segment of segments) {
    const [rangePart, stepPart] = segment.split('/');
    const step = stepPart ? Number(stepPart) : 1;

    if (!Number.isInteger(step) || step <= 0) {
      throw new Error(`Invalid step value "${stepPart}" in "${value}".`);
    }

    let rangeStart = min;
    let rangeEnd = max;

    if (rangePart !== '*') {
      const [startPart, endPart] = rangePart.split('-');
      rangeStart = Number(startPart);
      rangeEnd = endPart ? Number(endPart) : rangeStart;

      if (!Number.isInteger(rangeStart) || !Number.isInteger(rangeEnd)) {
        throw new Error(`Invalid range "${rangePart}" in "${value}".`);
      }
    }

    if (rangeStart < min || rangeEnd > max || rangeStart > rangeEnd) {
      throw new Error(`Field "${value}" is outside the supported range ${min}-${max}.`);
    }

    for (let current = rangeStart; current <= rangeEnd; current += step) {
      result.add(normalize ? normalize(current) : current);
    }
  }

  return {
    allowed: result,
    wildcard: false
  };
};

const matchesDay = (
  dayOfMonth: CronFieldResult,
  dayOfWeek: CronFieldResult,
  date: Date
): boolean => {
  const currentDayOfMonth = date.getUTCDate();
  const currentDayOfWeek = date.getUTCDay();

  const dayOfMonthMatch = dayOfMonth.allowed.has(currentDayOfMonth);
  const dayOfWeekMatch = dayOfWeek.allowed.has(currentDayOfWeek);

  if (dayOfMonth.wildcard && dayOfWeek.wildcard) {
    return true;
  }

  if (dayOfMonth.wildcard) {
    return dayOfWeekMatch;
  }

  if (dayOfWeek.wildcard) {
    return dayOfMonthMatch;
  }

  return dayOfMonthMatch || dayOfWeekMatch;
};

export const getNextRunFromCron = (expression: string, fromDate = new Date()): NextRunResult => {
  const upcomingRunsResult = getUpcomingRunsFromCron(expression, 1, fromDate);
  if (!upcomingRunsResult.success || !upcomingRunsResult.runs?.length) {
    return {
      success: false,
      message: upcomingRunsResult.message ?? 'Invalid cron expression.'
    };
  }

  return {
    success: true,
    nextRun: upcomingRunsResult.runs[0]
  };
};

export const getUpcomingRunsFromCron = (
  expression: string,
  count: number,
  fromDate = new Date()
): UpcomingRunsResult => {
  const fields = expression.trim().split(/\s+/);
  if (fields.length !== 5) {
    return {
      success: false,
      message: 'Cron expression must contain 5 fields.'
    };
  }

  if (!Number.isInteger(count) || count <= 0) {
    return {
      success: false,
      message: 'Run count must be greater than 0.'
    };
  }

  try {
    const minute = parseCronField(fields[0], 0, 59);
    const hour = parseCronField(fields[1], 0, 23);
    const dayOfMonth = parseCronField(fields[2], 1, 31);
    const month = parseCronField(fields[3], 1, 12);
    const dayOfWeek = parseCronField(fields[4], 0, 7, (value) => (value === 7 ? 0 : value));

    const runs: Date[] = [];
    const candidate = toShanghaiProxyDate(fromDate);

    while (runs.length < count) {
      candidate.setUTCSeconds(0, 0);
      candidate.setUTCMinutes(candidate.getUTCMinutes() + 1);

      const maxIterations = 60 * 24 * 366;
      let foundRun = false;

      for (let step = 0; step < maxIterations; step += 1) {
        const currentMonth = candidate.getUTCMonth() + 1;

        if (
          month.allowed.has(currentMonth) &&
          hour.allowed.has(candidate.getUTCHours()) &&
          minute.allowed.has(candidate.getUTCMinutes()) &&
          matchesDay(dayOfMonth, dayOfWeek, candidate)
        ) {
          runs.push(
            fromShanghaiParts(
              candidate.getUTCFullYear(),
              candidate.getUTCMonth() + 1,
              candidate.getUTCDate(),
              candidate.getUTCHours(),
              candidate.getUTCMinutes()
            )
          );
          foundRun = true;
          break;
        }

        candidate.setUTCMinutes(candidate.getUTCMinutes() + 1);
      }

      if (!foundRun) {
        return {
          success: false,
          message: 'Unable to find the next run time within 366 days.'
        };
      }
    }

    return {
      success: true,
      runs
    };
  } catch (error) {
    return {
      success: false,
      message: error instanceof Error ? error.message : 'Invalid cron expression.'
    };
  }
};

export const formatDateTime = (value: Date): string => {
  const { year, month, day, hour, minute, second } = toShanghaiParts(value);
  const formattedMonth = String(month).padStart(2, '0');
  const formattedDay = String(day).padStart(2, '0');
  const formattedHour = String(hour).padStart(2, '0');
  const formattedMinute = String(minute).padStart(2, '0');
  const formattedSecond = String(second).padStart(2, '0');

  return `${year}-${formattedMonth}-${formattedDay} ${formattedHour}:${formattedMinute}:${formattedSecond}`;
};

export const parseDateTime = (value: string): ParseDateTimeResult => {
  const trimmed = value.trim();
  const match = trimmed.match(/^(\d{4})-(\d{2})-(\d{2}) (\d{2}):(\d{2}):(\d{2})$/);

  if (!match) {
    return {
      success: false,
      message: 'Please use YYYY-MM-DD HH:MM:SS format.'
    };
  }

  const [, yearText, monthText, dayText, hourText, minuteText, secondText] = match;
  const year = Number(yearText);
  const month = Number(monthText);
  const day = Number(dayText);
  const hour = Number(hourText);
  const minute = Number(minuteText);
  const second = Number(secondText);

  if (
    !Number.isInteger(year) ||
    !Number.isInteger(month) ||
    !Number.isInteger(day) ||
    !Number.isInteger(hour) ||
    !Number.isInteger(minute) ||
    !Number.isInteger(second) ||
    month < 1 ||
    month > 12 ||
    day < 1 ||
    day > 31 ||
    hour < 0 ||
    hour > 23 ||
    minute < 0 ||
    minute > 59 ||
    second < 0 ||
    second > 59
  ) {
    return {
      success: false,
      message: 'Please enter a valid date and time.'
    };
  }

  const date = fromShanghaiParts(year, month, day, hour, minute, second);
  if (formatDateTime(date) !== trimmed) {
    return {
      success: false,
      message: 'Please enter a valid date and time.'
    };
  }

  return {
    success: true,
    date
  };
};

export const getShanghaiStartOfDay = (dayOffset = 0, fromDate = new Date()): Date => {
  const { year, month, day } = toShanghaiParts(fromDate);
  const baseDate = fromShanghaiParts(year, month, day, 0, 0, 0);
  baseDate.setUTCDate(baseDate.getUTCDate() + dayOffset);
  return baseDate;
};
