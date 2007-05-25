package bluej.groupwork;

/**
 * Class to filter CVS StatusInformation to calculate those classes that will 
 * be changed when we next commit. It should include files that are locally 
 * modified, remotely modified, locally deleted and remotely removed.
 *
 * @author bquig
 * @version $Id: CommitFilter.java 5058 2007-05-25 04:40:45Z davmac $
 */
public class CommitFilter
{
    /**
     * Filter to identify which files in a repository will be altered at 
     * the next commit.
     */
    public boolean accept(TeamStatusInfo statusInfo)
    {
        if (statusInfo.getStatus() == TeamStatusInfo.STATUS_DELETED) {
            return true;
        }
        if (statusInfo.getStatus() == TeamStatusInfo.STATUS_NEEDSADD) {
            return true;
        }
        if (statusInfo.getStatus() == TeamStatusInfo.STATUS_NEEDSCOMMIT) {
            return true;
        }

        return false;
    }
}
