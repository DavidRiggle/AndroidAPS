package info.nightscout.androidaps.plugins.pump.omnipod

import android.graphics.Color
import android.graphics.PorterDuff
import android.os.Bundle
import android.os.Handler
import android.text.SpannableString
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import info.nightscout.androidaps.MainApp
import info.nightscout.androidaps.R
import info.nightscout.androidaps.events.EventTempBasalChange
import info.nightscout.androidaps.logging.L
import info.nightscout.androidaps.plugins.bus.RxBus
import info.nightscout.androidaps.plugins.pump.omnipod.events.EventOmnipodUpdateGui
import info.nightscout.androidaps.plugins.pump.omnipod.history.OmniCoreCommandHistoryItem
import info.nightscout.androidaps.plugins.pump.omnipod.history.OmnicoreCommandHistoryStatus
import info.nightscout.androidaps.utils.DateUtil
import info.nightscout.androidaps.utils.FabricPrivacy
import info.nightscout.androidaps.utils.T
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.CompositeDisposable
import kotlinx.android.synthetic.main.omnicore_fragment.*
import org.slf4j.LoggerFactory

class OmniCorePumpFragment : Fragment() {
    private val disposable = CompositeDisposable()
    private val log = LoggerFactory.getLogger(L.PUMP)


    private val loopHandler = Handler()
    private lateinit var refreshLoop: Runnable

    init {
        refreshLoop = Runnable {
            activity?.runOnUiThread { updateGui() }
            loopHandler.postDelayed(refreshLoop, T.mins(1).msecs())
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {

        val view = inflater.inflate(R.layout.omnicore_fragment, container, false)


        return view
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {

        omnicore_updatestatusbutton.setOnClickListener {
            if (L.isEnabled(L.PUMP))
                log.debug("Omnicore Status Button clicked")
            OmnipodPlugin.getPlugin().pdm.getPodStatus()
        }

        omnicore_launchomnicorebutton.setOnClickListener {if (L.isEnabled(L.PUMP))
            log.debug("Omnicore Launch Button clicked")
            OmnipodPlugin.getPlugin().openOmnicore(context,MainApp.gs(R.string.omnicore_package_name));
        }

        var historyItemAdapter = ListAdapter(OmnipodPlugin.getPlugin().pdm.commandHistory.allHistory)
        var historyItemlayoutManager = LinearLayoutManager(activity);
        historyItemlayoutManager.reverseLayout = true
        historyItemlayoutManager.stackFromEnd = true
        omnicorestatus_history_list.apply{
            layoutManager = historyItemlayoutManager
            // set the custom adapter to the RecyclerView
            adapter = historyItemAdapter
        }

    }

    @Synchronized
    override fun onResume() {
        super.onResume()
        disposable.add(RxBus
                .toObservable(EventOmnipodUpdateGui::class.java)
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe({ updateGui() }, { FabricPrivacy.logException(it) })
        )
        disposable.add(RxBus
                .toObservable(EventTempBasalChange::class.java)
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe({ updateGui() }, { FabricPrivacy.logException(it) })
        )

        loopHandler.postDelayed(refreshLoop, T.mins(1).msecs())
        updateGui()
    }

    @Synchronized
    override fun onPause() {
        super.onPause()
        disposable.clear()
        loopHandler.removeCallbacks(refreshLoop)
    }

    @Synchronized
    private fun updateGui() {
        if (L.isEnabled(L.PUMP))
            log.debug("Omnicore Fragment GUI Update")
        val omnicorePump = OmnipodPlugin.getPlugin()
        //val lastResult = OmniCoreResult.fromJson(SP.getString(R.string.key_omnicore_last_result, null));
        //val lastSuccessfulResult = OmniCoreResult.fromJson(SP.getString(R.string.key_omnicore_last_successful_result, null));

        val lastResult = omnicorePump.pdm.commandHistory.lastCommand
        val lastSuccessfulResult = omnicorePump.pdm.commandHistory.lastSuccess

        omnicorestatus_podid?.text= omnicorePump.serialNumber()
        omnicorestatus_connectionstatus?.text = omnicorePump.pdm.podStatusText
        if (omnicorePump.serialNumber() != "NO POD") {
            val reservoir = omnicorePump.pdm.GetReservoirLevel()
            omnicorestatus_reservoir?.text = if (reservoir > 50)  "> 50U" else reservoir.toString() + "U"

            omnicorestatus_podage?.text = DateUtil.dateAndTimeString(omnicorePump.pdm.expirationTime) + " - Pump Expiration"

            omnicorestatus_reservoir_empty?.text = DateUtil.dateAndTimeString(omnicorePump.pdm.reservoirTime) + " - Basal Exhaustion"
       }

        if (omnicorePump.pdm.lastStatusResponse > 0) {
            omnicorestatus_laststatustime?.text = DateUtil.minAgo(omnicorePump.pdm.lastStatusResponse)
        }
        else {
            omnicorestatus_laststatustime?.text = "Never"
        }

        if (lastResult != null) {
            omnicorestatus_lastcommand?.text = lastResult.request.requestDetails
            omnicorestatus_lastresult?.text = lastResult.status
            omnicorestatus_lastresulttime?.text = DateUtil.minAgo(lastResult.request.requested)

        }

        if (lastSuccessfulResult != null) {
            omnicorestatus_lastsuccess_command?.text = lastSuccessfulResult.request.requestDetails
            if (lastSuccessfulResult.result != null) {
                omnicorestatus_lastsuccess_time?.text = DateUtil.minAgo(lastSuccessfulResult.result.ResultDate)
            }
        }

        var historyList = ""
        val commandHistory = omnicorePump.pdm.commandHistory.allHistory
        var i = commandHistory.size
        while (i-- > 0) {
            historyList += ("Command: " + commandHistory.get(i).request.getRequestDetails()
                    + "\nStatus: " + commandHistory.get(i).status
                    + "\nTime: " + DateUtil.dateAndTimeString(commandHistory.get(i).request.requested)
                    + "\nProcessing: " + commandHistory.get(i).runTime + "ms\n\n")
            //     if ( _commandHistory.get(i).result != null) {
            //         historyList += "\nFullResponse: " + _commandHistory.get(i).result.asJson();
            //     }
        }

        omnicorestatus_commandhistory?.text = historyList

        omnicorestatus_history_list?.adapter?.notifyDataSetChanged()
    }




    class ListAdapter(private val list: List<OmniCoreCommandHistoryItem>)
        : RecyclerView.Adapter<HistoryItemHolder>() {

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): HistoryItemHolder {
            val inflater = LayoutInflater.from(parent.context)
            return HistoryItemHolder(inflater, parent)
        }

        override fun onBindViewHolder(holder: HistoryItemHolder, position: Int) {
            val historyItem: OmniCoreCommandHistoryItem = list[position]
            holder.bind(historyItem)
        }

        override fun getItemCount(): Int = list.size

        fun dataRefreshed() {
            notifyDataSetChanged();
        }

    }

    class HistoryItemHolder(inflater: LayoutInflater, parent: ViewGroup) :
            RecyclerView.ViewHolder(inflater.inflate(R.layout.omnicore_history_item, parent, false)) , View.OnLongClickListener {
        private var commandName: TextView? = null
        private var commandStatus: TextView? = null
        private var commandTime: TextView? = null
        private var commandRunTime: TextView? = null
        private var commandRssiRlVal: TextView? = null
        private var commandRssiPodVal: TextView? = null
        private var commandRssiRlChart: ProgressBar? = null
        private var commandRssiPodChart: ProgressBar? = null




        init {
            commandName = itemView.findViewById(R.id.omnicore_history_command)
            commandStatus = itemView.findViewById(R.id.omnicore_history_status)
            commandTime = itemView.findViewById(R.id.omnicore_history_time)
            commandRunTime = itemView.findViewById(R.id.omnicore_history_runtime)
            commandRssiRlVal = itemView.findViewById(R.id.omnicore_history_rssi_rl_value)
            commandRssiPodVal = itemView.findViewById(R.id.omnicore_history_rssi_pod_value)
            commandRssiRlChart = itemView.findViewById(R.id.omnicore_history_rssi_rl_chart)
            commandRssiPodChart = itemView.findViewById(R.id.omnicore_history_rssi_pod_chart)
            itemView.setOnLongClickListener(this)

        }

        override fun onLongClick(view: View): Boolean {
            val position = adapterPosition
            var title = ""
            var message = ""
            if (position >= 0) {
                var historyItem = OmnipodPlugin.getPlugin().pdm.commandHistory.getCommand(position)

                message = OmnipodPlugin.getPlugin().pdm.commandHistory.getCommand(position).toString()
                title = "Command Details: " + historyItem.request.requestDetails
            }
            else {
                message = "Cannot find Command"
                title = "Unknown Command"
            }

            var builder =  AlertDialog.Builder(view.context)
            builder.setTitle(title)
            builder.setIcon(MainApp.getIcon())
            val messageSpanned = SpannableString(message)
            builder.setMessage(messageSpanned)
            builder.setPositiveButton(MainApp.gs(R.string.ok), null)
            val alertDialog = builder.create()
            alertDialog.show()
                //((TextView) alertDialog.findViewById(android.R.id.message)).setMovementMethod(LinkMovementMethod.getInstance());
                //return true;

//            Toast.makeText(view.context, message, Toast.LENGTH_SHORT).show()
            // Return true to indicate the click was handled
            return true
        }


        fun bind(historyItem: OmniCoreCommandHistoryItem) {
            commandName?.text = historyItem.request.requestDetails
            commandStatus?.text = historyItem.status
            when (historyItem.status) {
                OmnicoreCommandHistoryStatus.PENDING.description -> commandStatus?.setTextColor(Color.YELLOW)
                OmnicoreCommandHistoryStatus.SUCCESS.description -> commandStatus?.setTextColor(Color.GREEN)
                OmnicoreCommandHistoryStatus.FAILED.description -> commandStatus?.setTextColor(Color.RED)
            }
            commandTime?.text = DateUtil.dateAndTimeString(historyItem.request.requested)
            commandRunTime?.text = historyItem.runTime.toString() + "ms"
            val rndRssiRl = historyItem.rssiRl
            val rndRssiPod =historyItem.rssiPod
            commandRssiRlVal?.text = "-" + rndRssiRl.toString()
            commandRssiRlChart?.progress = rndRssiRl
            when {
                rndRssiRl < 70 ->  commandRssiRlChart?.progressDrawable?.setColorFilter(Color.GREEN,PorterDuff.Mode.SRC_IN)
                rndRssiRl > 90 ->  commandRssiRlChart?.progressDrawable?.setColorFilter(Color.RED,PorterDuff.Mode.SRC_IN)
                else -> {
                    commandRssiRlChart?.progressDrawable?.setColorFilter(Color.YELLOW,PorterDuff.Mode.SRC_IN)
                }
            }
            commandRssiPodVal?.text = "-" + rndRssiPod.toString()
            commandRssiPodChart?.progress = rndRssiPod
            when {
                rndRssiPod < 70 ->  commandRssiPodChart?.progressDrawable?.setColorFilter(Color.GREEN,PorterDuff.Mode.SRC_IN)
                rndRssiPod > 90 ->  commandRssiPodChart?.progressDrawable?.setColorFilter(Color.RED,PorterDuff.Mode.SRC_IN)
                else -> {
                    commandRssiPodChart?.progressDrawable?.setColorFilter(Color.YELLOW,PorterDuff.Mode.SRC_IN)
                }
            }
        }
    }
}