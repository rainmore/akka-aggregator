def daemon = 'redlounge' // service name
/**
 * Set default logging level
 * http://logback.qos.ch/apidocs/ch/qos/logback/classic/Level.html#toLevel%28string%29
 */
def level = toLevel(System.getProperty('logging.level'), WARN)
def dir = '/var/log/redlounge'
def sysloggerHost = 'thoth.redlounge.io'

def patterns = [
    file:   '%date{yyyy-MM-dd HH:mm:ss.SSS} [%thread] %level %logger:%line - %message%n',
    syslog: daemon + ': [%thread] %level %logger:%line - %message',
    visual: '%date{yyyy-MM-dd HH:mm:ss.SSS} %gray([%thread]) %highlight(%level) %cyan(%logger{32}) : %green(%line) - %message%n'
]



appender('SYSLOG', SyslogAppender) {
    syslogHost = sysloggerHost //'thoth.redlounge.io'
    facility = 'daemon'
    suffixPattern = patterns.syslog
}

appender('STDOUT', ConsoleAppender) {
    encoder(PatternLayoutEncoder) {
        pattern = patterns.visual
    }
}

/* we do not use file logging by default, see SYSLOG appender above
appender('FILE', FileAppender) {
    file = dir + File.separator + daemon + '.log'
    encoder(PatternLayoutEncoder) {
        pattern = patterns.file
    }
}
*/

// World Manager components
logger('com.worldmanager.hedwig', null)

// Third-party libraries
logger('ch.qos.logback', WARN)

root(level, ['STDOUT', 'SYSLOG'])