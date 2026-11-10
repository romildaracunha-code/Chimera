package lab.nfc.core.storage
import android.app.job.JobInfo
import android.app.job.JobScheduler
import android.app.job.JobService
import android.app.job.JobParameters
import android.content.ComponentName
import android.content.Context
import org.json.JSONObject
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

/** Optional, persisted Android scheduling. Default is disabled; execution waits for idle + charging. */
object RetentionScheduler {
 const val JOB_ID=20038
 fun policy(repo:SecureRepository)=repo.read("settings/retention")?.let { JSONObject(it.toString(Charsets.UTF_8)) } ?: JSONObject().put("enabled",false).put("days",90)
 fun configure(context:Context,repo:SecureRepository,enabled:Boolean,days:Int) {
  require(days in 1..3650)
  repo.write("settings/retention",JSONObject().put("enabled",enabled).put("days",days).put("updatedAt",System.currentTimeMillis()).toString().toByteArray())
  reconcile(context,repo)
 }
 fun reconcile(context:Context,repo:SecureRepository) {
  val scheduler=context.getSystemService(JobScheduler::class.java);val policy=policy(repo)
  if(!policy.getBoolean("enabled")) { scheduler.cancel(JOB_ID);return }
  require(policy.getInt("days") in 1..3650)
  val job=JobInfo.Builder(JOB_ID,ComponentName(context,RetentionJobService::class.java))
   .setPeriodic(24*60*60*1000L).setRequiresDeviceIdle(true).setRequiresCharging(true).setPersisted(true).build()
  check(scheduler.schedule(job)==JobScheduler.RESULT_SUCCESS) { "Android não aceitou o agendamento; política salva, reabra para tentar novamente" }
 }
}
class RetentionJobService:JobService() {
 private val worker=Executors.newSingleThreadExecutor()
 private val tasks=java.util.concurrent.ConcurrentHashMap<JobParameters,AtomicBoolean>()
 override fun onStartJob(params:JobParameters):Boolean {
  val cancelled=AtomicBoolean(false);tasks[params]=cancelled
  worker.execute {
   var retry=false
   try {
    val repo=SecureRepository.get(this);val policy=RetentionScheduler.policy(repo)
    if(policy.getBoolean("enabled") && !cancelled.get()) {
     WorkspaceBootstrap.prepare(this)
     // Never prune while a laboratory acquisition is active.
     val store=LabStore.get(this)
     if(store.activeHce!=null || store.physicalCaptureActive || store.allSessions().any { it.result=="Em andamento" }) retry=true
     else {
      val ledger=WorkspaceBootstrap.getLedger(this)
      val plan=ledger.retentionPreview(policy.getInt("days"))
      if(!cancelled.get()) ledger.applyRetention(plan) { !cancelled.get() && store.activeHce==null && !store.physicalCaptureActive && RetentionScheduler.policy(repo).getBoolean("enabled") }
     }
    }
   } catch(e:Exception) { android.util.Log.e("NfcLab","Scheduled retention did not complete; inspect evidence journal",e);retry=true }
   finally { tasks.remove(params);jobFinished(params,retry && !cancelled.get()) }
  }
  return true
 }
 override fun onStopJob(params:JobParameters):Boolean { tasks[params]?.set(true);return true }
 override fun onDestroy() { tasks.values.forEach { it.set(true) };worker.shutdown();super.onDestroy() }
}
